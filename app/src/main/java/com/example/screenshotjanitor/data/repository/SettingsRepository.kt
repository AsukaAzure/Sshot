package com.example.screenshotjanitor.data.repository

import android.content.Context
import androidx.core.content.edit
import com.example.screenshotjanitor.core.constants.AppConstants

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences(AppConstants.PREF_NAME, Context.MODE_PRIVATE)

    fun isAutoArchiveEnabled(): Boolean {
        return prefs.getBoolean(AppConstants.PREF_AUTO_ARCHIVE, false)
    }

    fun setAutoArchiveEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(AppConstants.PREF_AUTO_ARCHIVE, enabled) }
    }
}
