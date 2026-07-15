package dev.sj010.ssjanitor

import android.app.Application
import android.content.Intent
import dev.sj010.ssjanitor.data.db.AppDatabase
import dev.sj010.ssjanitor.data.repository.ScreenshotRepository
import dev.sj010.ssjanitor.data.repository.SettingsRepository
import dev.sj010.ssjanitor.observer.ScreenshotContentObserver
import dev.sj010.ssjanitor.service.ScreenshotDetectionService

class SshotApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val repository: ScreenshotRepository by lazy { ScreenshotRepository(database.screenshotDao()) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    var contentObserver: ScreenshotContentObserver? = null

    override fun onCreate() {
        super.onCreate()
        startDetectionService()
    }

    fun startDetectionService() {
        val intent = Intent(this, ScreenshotDetectionService::class.java)
        startForegroundService(intent)
    }
}
