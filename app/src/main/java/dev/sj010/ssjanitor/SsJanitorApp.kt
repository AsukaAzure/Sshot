package dev.sj010.ssjanitor

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import dev.sj010.ssjanitor.core.constants.AppMode
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
        if (settingsRepository.getAppMode() == AppMode.RUNNING) {
            scheduleCleanupWorker()
            startDetectionService()
        }
    }

    fun startDetectionService() {
        val intent = Intent(this, ScreenshotDetectionService::class.java)
        try {
            startForegroundService(intent)
        } catch (e: IllegalStateException) {
            // WorkManager can cold-start this process in the background to run
            // cleanup, where foreground-service starts are restricted (Android 12+).
            // Monitoring restarts via BootReceiver or the next foreground launch.
            Log.w("SshotApp", "Skipped starting detection service from background", e)
        }
    }

    fun stopDetectionService() {
        stopService(Intent(this, ScreenshotDetectionService::class.java))
    }

    /**
     * Pause = fully off: stops monitoring and cancels every piece of background
     * work, so nothing runs until resumed. Scheduled deletions are still tracked
     * in the DB; the safety-net worker re-armed by [resumeApp] deletes anything
     * whose time has passed.
     */
    fun pauseApp() {
        settingsRepository.setCleanupPaused(true)
        stopDetectionService()
        WorkManager.getInstance(this).cancelAllWork()
    }

    /** Resumes monitoring and re-arms the daily safety-net cleanup. */
    fun resumeApp() {
        // Mark the session start BEFORE the detector runs its initial scan, so
        // screenshots captured while paused are never surfaced on resume.
        settingsRepository.setMonitoringSince(System.currentTimeMillis())
        settingsRepository.setCleanupPaused(false)
        startDetectionService()
        scheduleCleanupWorker()
    }

    /**
     * Daily safety-net cleanup: deletes archived screenshots and any whose
     * scheduled deleteAt has passed, even if their per-URI one-time work was
     * skipped (e.g. it fired while Sshot was paused).
     */
    fun scheduleCleanupWorker() {
        val cleanupRequest = PeriodicWorkRequestBuilder<ScreenshotCleanupWorker>(24, TimeUnit.HOURS)
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
