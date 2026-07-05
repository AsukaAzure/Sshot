package dev.sj010.ssjanitor.core.constants

object AppConstants {
    const val NOTIFICATION_CHANNEL_ID = "sshot_channel"
    const val NOTIFICATION_CHANNEL_NAME = "Screenshot Detection"
    const val NOTIFICATION_CHANNEL_DESC = "Notifications for new screenshots with actions"
    const val NOTIFICATION_ID = 1001
    const val NOTIFICATION_CLEANUP_ID = 1002
    const val NOTIFICATION_SERVICE_ID = 1003
    const val NOTIFICATION_UPDATE_ID = 1004

    const val NOTIFICATION_SERVICE_CHANNEL_ID = "sshot_service_channel"
    const val NOTIFICATION_SERVICE_CHANNEL_NAME = "Background Detection"

    const val NOTIFICATION_UPDATE_CHANNEL_ID = "sshot_update_channel"
    const val NOTIFICATION_UPDATE_CHANNEL_NAME = "App Updates"

    const val ACTION_ARCHIVE = "dev.sj010.ssjanitor.ACTION_ARCHIVE"
    const val ACTION_KEEP = "dev.sj010.ssjanitor.ACTION_KEEP"
    const val ACTION_DELETE = "dev.sj010.ssjanitor.ACTION_DELETE"
    const val ACTION_CLEANUP_OLD = "dev.sj010.ssjanitor.ACTION_CLEANUP_OLD"

    const val EXTRA_SCREENSHOT_URI = "extra_screenshot_uri"

    const val PREF_NAME = "sshot_prefs"
    const val PREF_AUTO_ARCHIVE = "pref_auto_archive"
    const val PREF_CLEANUP_PAUSED = "pref_cleanup_paused"
    const val PREF_OVERLAY_RIGHT = "pref_overlay_right"

    const val GITHUB_REPO_URL = "https://github.com/AsukaAzure/Sshot"
    const val GITHUB_API_LATEST_RELEASE = "https://api.github.com/repos/AsukaAzure/Sshot/releases/latest"
}

