package dev.sj010.ssjanitor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.sj010.ssjanitor.SshotApp
import dev.sj010.ssjanitor.core.constants.AppMode

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val app = context.applicationContext as SshotApp
            // Only restart monitoring if Sshot is running — paused/off stay off.
            if (app.settingsRepository.getAppMode() != AppMode.RUNNING) return
            val pendingResult = goAsync()
            app.startDetectionService()
            pendingResult.finish()
        }
    }
}
