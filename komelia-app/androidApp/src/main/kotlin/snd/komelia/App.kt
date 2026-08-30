package snd.komelia

import android.app.Application
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_LOW
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.MutableStateFlow
import snd.komelia.offline.local.LocalLibraryScanWorker
import snd.komelia.offline.sync.downloadChannelId
import snd.komelia.ui.DependencyContainer
import java.util.concurrent.TimeUnit

val dependencies = MutableStateFlow<DependencyContainer?>(null)
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        GlobalExceptionHandler.initialize(applicationContext)
        setupNotificationChannels()
        initWorkManager()
    }

    private fun setupNotificationChannels() {
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat
                    .Builder(downloadChannelId, IMPORTANCE_LOW)
                    .setName("downloads")
                    .setShowBadge(false)
                    .build()
            )
        )
    }

    private fun initWorkManager() {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setWorkerFactory(
                MyWorkerFactory { context ->
                    initializeDependencies(context).offlineDependencies
                }
            )
            .build()
        WorkManager.initialize(this, config)
        val request = PeriodicWorkRequestBuilder<LocalLibraryScanWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "local-library-scan",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
