package snd.komelia.updates

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION
import android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
import android.os.Build
import androidx.core.net.toUri
import androidx.core.content.IntentCompat
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.*
import io.ktor.http.isSuccess
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.io.readByteArray
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

class AndroidAppUpdater(
    private val githubClient: UpdateClient,
    private val context: Context,
    override val installsInApp: Boolean = true,
) : AppUpdater {
    private var inProgress = AtomicBoolean(false)

    override suspend fun getReleases(): List<AppRelease> {
        val releases = githubClient.getKomeliaReleases()
        return coroutineScope { releases.map { release -> async { release.toAppRelease() } }.awaitAll() }
    }

    override suspend fun updateToLatest(): Flow<UpdateProgress>? {
        val latest = githubClient.getKomeliaLatestRelease().toAppRelease()
        return updateTo(latest)
    }

    override fun updateTo(release: AppRelease): Flow<UpdateProgress>? {
        if (!installsInApp) {
            context.startActivity(Intent(Intent.ACTION_VIEW, release.htmlUrl.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return null
        }
        val assetUrl = release.assetUrl
        if (assetUrl == null) {
            context.startActivity(Intent(Intent.ACTION_VIEW, release.htmlUrl.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return null
        }
        if (!inProgress.compareAndSet(false, true)) return null

        return flow {
            try {
                try {
                    installFrom(assetUrl, release.assetSha256)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (failure !is IOException && failure !is ResponseException) throw failure
                    val fallback = release.fallbackAssetUrl ?: throw failure
                    try {
                        installFrom(fallback, null)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (fallbackFailure: Exception) {
                        fallbackFailure.addSuppressed(failure)
                        throw fallbackFailure
                    }
                }
            } finally {
                inProgress.set(false)
            }
        }
    }

    private suspend fun FlowCollector<UpdateProgress>.installFrom(url: String, sha256: String?) {
        var session: PackageInstaller.Session? = null
        val installer = context.packageManager.packageInstaller
        var sessionId: Int? = null
        var committed = false
        try {
            emit(UpdateProgress(0, 0))
            val activeId = installer.createSession(PackageInstaller.SessionParams(MODE_FULL_INSTALL))
            sessionId = activeId
            val activeSession = installer.openSession(activeId)
            session = activeSession
            githubClient.streamFile(url) { response -> streamToSession(response, activeSession, sha256) }
            val receiverIntent = Intent(context, PackageInstallerStatusReceiver::class.java)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val receiverPendingIntent = PendingIntent.getBroadcast(context, 0, receiverIntent, flags)
            activeSession.commit(receiverPendingIntent.intentSender)
            committed = true
        } finally {
            if (!committed) runCatching { sessionId?.let { installer.abandonSession(it) } }
            runCatching { session?.close() }
        }
    }

    private suspend fun FlowCollector<UpdateProgress>.streamToSession(
        response: HttpResponse,
        session: PackageInstaller.Session,
        expectedSha256: String?,
    ) {
        if (!response.status.isSuccess()) throw IOException("APK download failed: ${response.status}")
        val length = response.headers["Content-Length"]?.toLong() ?: 0L
        emit(UpdateProgress(length, 0))
        val channel = response.bodyAsChannel().counted()
        val digest = expectedSha256?.let { MessageDigest.getInstance("SHA-256") }
        val sessionStream = session.openWrite("komelia", 0, -1)
        sessionStream.buffered().use { bufferedSessionStream ->
            while (!channel.isClosedForRead) {

                val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                while (!packet.exhausted()) {
                    val bytes = packet.readByteArray()
                    bufferedSessionStream.write(bytes)
                    digest?.update(bytes)
                }
                emit(UpdateProgress(length, channel.totalBytesRead))
            }
            bufferedSessionStream.flush()
            val actualSha256 = digest?.digest()?.joinToString("") { "%02x".format(it.toInt() and 0xff) }
            if (expectedSha256 != null && actualSha256 != expectedSha256) {
                throw IOException("Downloaded APK checksum mismatch")
            }
            session.fsync(sessionStream)
        }
    }

    private suspend fun GithubRelease.toAppRelease(): AppRelease {
        val version = AppVersion.fromString(tagName)
        val manifestAsset = if (installsInApp) assets.singleOrNull { it.name == ANDROID_ABI_MANIFEST } else null
        val manifest = if (manifestAsset == null) null else try {
            githubClient.getAndroidAbiManifest(manifestAsset.browserDownloadUrl)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val asset = selectAndroidApk(version, Build.SUPPORTED_ABIS.toList(), manifest, assets)
        val universal = selectAndroidApk(version, emptyList(), null, assets)

        return AppRelease(
            version = version,
            publishDate = publishedAt,
            releaseNotesBody = body.replace("\r", ""),
            htmlUrl = htmlUrl,
            assetName = asset?.name,
            assetUrl = asset?.url,
            assetSha256 = asset?.sha256,
            fallbackAssetUrl = universal?.url?.takeIf { asset?.sha256 != null && it != asset.url },
        )
    }
}

class PackageInstallerStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            STATUS_PENDING_USER_ACTION -> {
                val confirmationIntent = IntentCompat.getParcelableExtra(
                    intent, Intent.EXTRA_INTENT, Intent::class.java
                )
                if (confirmationIntent != null) {
                    context.startActivity(confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }

            else -> {}
        }
    }
}
