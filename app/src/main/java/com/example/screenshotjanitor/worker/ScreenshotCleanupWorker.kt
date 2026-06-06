package com.example.screenshotjanitor.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.screenshotjanitor.data.db.AppDatabase
import com.example.screenshotjanitor.data.repository.ScreenshotRepository

class ScreenshotCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "ScreenshotCleanupWorker"
    private val autoDelete = true

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting periodic screenshot cleanup task (autoDelete=$autoDelete) at ${System.currentTimeMillis()}")

        val database = AppDatabase.getDatabase(applicationContext)
        val repository = ScreenshotRepository(database.screenshotDao())

        try {
            // Only target screenshots the user explicitly archived (not kept, not already deleted)
            val archivedScreenshots = repository.getArchivedForCleanup()
            Log.d(TAG, "Scheduled Cleanup: Found ${archivedScreenshots.size} archived screenshots to clean up")
            
            archivedScreenshots.forEach { 
                Log.d(TAG, "Target for cleanup: ${it.uri} (created at ${it.createdAt})")
            }

            if (archivedScreenshots.isNotEmpty()) {
                val notificationManager = com.example.screenshotjanitor.notifications.ScreenshotNotificationManager(applicationContext)
                if (autoDelete) {
                    Log.d(TAG, "Executing auto-deletion for ${archivedScreenshots.size} items")
                    val successfullyDeleted = repository.deleteScreenshotsDirectly(
                        applicationContext,
                        archivedScreenshots.map { it.uri }
                    )
                    
                    Log.d(TAG, "Successfully deleted ${successfullyDeleted.size} screenshots from storage")
                    if (successfullyDeleted.isNotEmpty()) {
                        repository.markAsDeleted(successfullyDeleted)
                        notificationManager.showAutoCleanupNotification(successfullyDeleted.size)
                    }
                    
                    val failedDeletions = archivedScreenshots.filter { it.uri !in successfullyDeleted }
                    if (failedDeletions.isNotEmpty()) {
                        Log.w(TAG, "Failed to delete ${failedDeletions.size} screenshots: ${failedDeletions.map { it.uri }}")
                        notificationManager.showCleanupNotification(failedDeletions.size)
                    }
                } else {
                    Log.d(TAG, "autoDelete is false, just showing recommendation notification for ${archivedScreenshots.size} items")
                    notificationManager.showCleanupNotification(archivedScreenshots.size)
                }
            } else {
                Log.d(TAG, "No archived screenshots found for cleanup this time.")
            }
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL: Error executing screenshot cleanup worker", e)
            return Result.retry()
        }
    }
}
