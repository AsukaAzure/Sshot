package dev.sj010.ssjanitor.qs

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.sj010.ssjanitor.SshotApp
import dev.sj010.ssjanitor.core.constants.AppMode

/**
 * Quick Settings tile to pause/resume Sshot without opening the app.
 * Active = running, inactive = fully paused (no detection, no background work).
 */
class PauseTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val app = applicationContext as SshotApp
        if (app.settingsRepository.getAppMode() == AppMode.RUNNING) {
            app.pauseApp()
        } else {
            app.resumeApp()
        }
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val running =
            (applicationContext as SshotApp).settingsRepository.getAppMode() == AppMode.RUNNING
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (running) "Running" else "Paused"
        tile.updateTile()
    }
}
