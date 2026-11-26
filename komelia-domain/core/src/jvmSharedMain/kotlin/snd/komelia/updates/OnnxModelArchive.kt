package snd.komelia.updates

import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.counted
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.io.readByteArray
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import java.util.zip.ZipFile
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.outputStream

internal class OnnxModelArchive(
    private val destination: Path,
    private val requiredFiles: Set<String>,
    private val alternativeFiles: Set<String> = emptySet(),
) {
    suspend fun install(download: suspend (Path) -> Unit) {
        val parent = destination.toAbsolutePath().parent.createDirectories()
        val stage = Files.createTempDirectory(parent, ".onnx-model-")
        val archive = stage.resolve("download.zip")
        val models = stage.resolve("models")
        val backup = stage.resolve("previous")
        var installed = false
        try {
            download(archive)
            val extracted = extract(archive, models.createDirectories())
            currentCoroutineContext().ensureActive()
            if (extracted.size == 1) {
                // The panel model directory can contain other application data.
                destination.createDirectories()
                val filename = extracted.single()
                Files.move(models.resolve(filename), destination.resolve(filename), ATOMIC_MOVE, REPLACE_EXISTING)
            } else {
                replaceModelDirectory(models, backup)
            }
            installed = true
        } finally {
            // If rollback fails, retain the original files in the backup directory.
            if (installed || !backup.exists()) {
                stage.toFile().deleteRecursively()
            } else {
                archive.toFile().delete()
                models.toFile().deleteRecursively()
            }
        }
    }

    private suspend fun extract(archive: Path, models: Path): Set<String> {
        val extracted = mutableSetOf<String>()
        ZipFile(archive.toFile()).use { zip ->
            for (entry in zip.entries()) {
                currentCoroutineContext().ensureActive()
                val name = entry.name.substringAfterLast('/').substringAfterLast('\\')
                if (entry.isDirectory || name !in requiredFiles && name !in alternativeFiles) continue
                check(extracted.add(name)) { "Duplicate ONNX model: $name" }
                val crc = CRC32()
                val bytes = CheckedInputStream(zip.getInputStream(entry), crc).use { source ->
                    models.resolve(name).outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var count = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = source.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            count += read
                        }
                        count
                    }
                }
                check(bytes > 0 && bytes == entry.size && crc.value == entry.crc) { "Invalid ONNX model: $name" }
            }
        }
        check(extracted.containsAll(requiredFiles) &&
            (alternativeFiles.isEmpty() || alternativeFiles.any(extracted::contains))) {
            "The archive does not contain the required ONNX models"
        }
        return extracted
    }

    private fun replaceModelDirectory(models: Path, backup: Path) {
        if (destination.exists()) Files.move(destination, backup, ATOMIC_MOVE)
        try {
            Files.move(models, destination, ATOMIC_MOVE)
        } catch (error: Exception) {
            if (backup.exists()) {
                try {
                    Files.move(backup, destination, ATOMIC_MOVE)
                } catch (rollbackError: Exception) {
                    error.addSuppressed(rollbackError)
                }
            }
            throw error
        }
    }
}

internal suspend fun FlowCollector<UpdateProgress>.downloadOnnxModel(
    client: UpdateClient,
    url: String,
    target: OnnxModelArchive,
) {
    emit(UpdateProgress(0, 0))
    target.install { archive ->
        client.streamFile(url) { response ->
            check(response.status.isSuccess()) { "ONNX model download returned HTTP ${response.status.value}" }
            val length = response.headers["Content-Length"]?.toLongOrNull() ?: 0L
            val channel = response.bodyAsChannel().counted()
            archive.outputStream().buffered().use { output ->
                while (!channel.isClosedForRead) {
                    currentCoroutineContext().ensureActive()
                    val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                    while (!packet.exhausted()) output.write(packet.readByteArray())
                    emit(UpdateProgress(length, channel.totalBytesRead))
                }
            }
            check(length <= 0 || length == channel.totalBytesRead) { "Incomplete ONNX model download" }
        }
        emit(UpdateProgress(0, 0))
    }
}
