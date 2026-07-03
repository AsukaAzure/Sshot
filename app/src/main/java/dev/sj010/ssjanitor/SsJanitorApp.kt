package dev.sj010.ssjanitor

import android.app.Application
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import dev.sj010.ssjanitor.data.db.AppDatabase
import dev.sj010.ssjanitor.data.repository.ScreenshotRepository
import dev.sj010.ssjanitor.data.repository.SettingsRepository
import dev.sj010.ssjanitor.observer.ScreenshotContentObserver
import dev.sj010.ssjanitor.service.ScreenshotDetectionService
import dev.sj010.ssjanitor.worker.ScreenshotCleanupWorker
import java.util.concurrent.TimeUnit

class SshotApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val repository: ScreenshotRepository by lazy { ScreenshotRepository(database.screenshotDao()) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    var contentObserver: ScreenshotContentObserver? = null

    override fun onCreate() {
        super.onCreate()
        startDetectionService()
        scheduleCleanupWorker()
    }

    fun startDetectionService() {
        val intent = Intent(this, ScreenshotDetectionService::class.java)
        startForegroundService(intent)
    }

    private fun scheduleCleanupWorker() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        val cleanupRequest = PeriodicWorkRequestBuilder<ScreenshotCleanupWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ScreenshotCleanupWork",
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupRequest
        )
    }
}
