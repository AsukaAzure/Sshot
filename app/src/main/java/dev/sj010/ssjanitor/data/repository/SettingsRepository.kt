package dev.sj010.ssjanitor.data.repository

import android.content.Context
import androidx.core.content.edit
import dev.sj010.ssjanitor.core.constants.AppConstants

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences(AppConstants.PREF_NAME, Context.MODE_PRIVATE)

    fun isAutoArchiveEnabled(): Boolean {
        return prefs.getBoolean(AppConstants.PREF_AUTO_ARCHIVE, false)
    }

    fun setAutoArchiveEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(AppConstants.PREF_AUTO_ARCHIVE, enabled) }
    }

    fun isCleanupPaused(): Boolean {
        return prefs.getBoolean(AppConstants.PREF_CLEANUP_PAUSED, true)
    }

    fun setCleanupPaused(paused: Boolean) {
        prefs.edit { putBoolean(AppConstants.PREF_CLEANUP_PAUSED, paused) }
    }

    fun isOverlayOnRightSide(): Boolean {
        return prefs.getBoolean(AppConstants.PREF_OVERLAY_RIGHT, false)
    }

    fun setOverlayOnRightSide(right: Boolean) {
        prefs.edit { putBoolean(AppConstants.PREF_OVERLAY_RIGHT, right) }
    }

    fun getPreset1Minutes(): Int = prefs.getInt(AppConstants.PREF_PRESET_1_MINUTES, 60)
    fun setPreset1Minutes(minutes: Int) = prefs.edit { putInt(AppConstants.PREF_PRESET_1_MINUTES, minutes) }

    fun getPreset2Minutes(): Int = prefs.getInt(AppConstants.PREF_PRESET_2_MINUTES, 720)
    fun setPreset2Minutes(minutes: Int) = prefs.edit { putInt(AppConstants.PREF_PRESET_2_MINUTES, minutes) }

    fun getPreset3Minutes(): Int = prefs.getInt(AppConstants.PREF_PRESET_3_MINUTES, 4320)
    fun setPreset3Minutes(minutes: Int) = prefs.edit { putInt(AppConstants.PREF_PRESET_3_MINUTES, minutes) }
}
