package dev.sj010.ssjanitor.network

import android.content.Context
import dev.sj010.ssjanitor.BuildConfig
import dev.sj010.ssjanitor.core.constants.AppConstants
import dev.sj010.ssjanitor.notifications.ScreenshotNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val html_url: String
)

class UpdateManager(private val context: Context) {
    private val notificationManager = ScreenshotNotificationManager(context)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdates(): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL(AppConstants.GITHUB_API_LATEST_RELEASE)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            
            if (connection.responseCode == 200) {
                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                val release = json.decodeFromString<GitHubRelease>(responseBody)
                val latestVersion = release.tag_name.removePrefix("v")
                val currentVersion = BuildConfig.VERSION_NAME

                if (isNewerVersion(currentVersion, latestVersion)) {
                    notificationManager.showUpdateNotification(release.tag_name)
                    return@withContext latestVersion
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        
        val size = minOf(currentParts.size, latestParts.size)
        for (i in 0 until size) {
            if (latestParts[i] > currentParts[i]) return true
            if (latestParts[i] < currentParts[i]) return false
        }
        return latestParts.size > currentParts.size
    }
}
