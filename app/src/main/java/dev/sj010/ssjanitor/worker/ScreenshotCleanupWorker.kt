package dev.sj010.ssjanitor.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.sj010.ssjanitor.SsJanitorApp
import dev.sj010.ssjanitor.notifications.ScreenshotNotificationManager

class ScreenshotCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as SsJanitorApp
        if (app.settingsRepository.isCleanupPaused()) {
            return Result.success()
        }

        val repository = app.repository

        return try {
            val toCleanup = repository.getScreenshotsForCleanup()
            if (toCleanup.isNotEmpty()) {
                val nm = ScreenshotNotificationManager(applicationContext)
                val deleted = repository.deleteScreenshotsDirectly(
                    applicationContext,
                    toCleanup.map { it.uri }
                )
                if (deleted.isNotEmpty()) {
                    repository.markAsDeleted(deleted)
                    nm.showAutoCleanupNotification(deleted.size)
                }
                val failed = toCleanup.size - deleted.size
                if (failed > 0) {
                    nm.showCleanupNotification(failed)
                }
            }
            val app = applicationContext as SsJanitorApp
            app.contentObserver?.clearProcessedUris()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
