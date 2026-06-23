package com.example.screenshotjanitor.observer

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.example.screenshotjanitor.data.db.AppDatabase
import com.example.screenshotjanitor.data.db.entity.ScreenshotEntity
import com.example.screenshotjanitor.data.repository.ScreenshotRepository
import com.example.screenshotjanitor.data.repository.SettingsRepository
import com.example.screenshotjanitor.notifications.ScreenshotNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Collections

class ScreenshotContentObserver(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    handler: Handler = Handler(Looper.getMainLooper())
) : ContentObserver(handler) {

    private val database by lazy { AppDatabase.getDatabase(context) }
    private val repository by lazy { ScreenshotRepository(database.screenshotDao()) }
    private val notificationManager by lazy { ScreenshotNotificationManager(context) }

    private val processedUris = Collections.synchronizedSet(mutableSetOf<String>())
    private val pendingUris = Collections.synchronizedSet(mutableSetOf<String>())
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "ScreenshotObserver"

        /** Cap on in-memory dedup set to prevent unbounded growth. */
        private const val MAX_PROCESSED_URIS = 200

        /**
         * Exponential-ish backoff delays between query retries.
         *
         * On a cold MediaStore process the first image insert can take well over
         * the old 1.5 s window before DISPLAY_NAME / RELATIVE_PATH are populated.
         * This sequence gives ~10 s of total retry coverage.
         */
        private val RETRY_DELAYS_MS = longArrayOf(200, 300, 500, 800, 1000, 1500, 2000, 2000, 2000)

        /** Look-back window (seconds) for the initial scan on observer registration. */
        private const val INITIAL_SCAN_WINDOW_SECONDS = 30L

        /** Look-back window (seconds) for the fallback scan after URI retries fail. */
        private const val FALLBACK_SCAN_WINDOW_SECONDS = 60L
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /**
     * Called immediately after the observer is registered with ContentResolver.
     *
     * Scans for screenshots that may have been captured during process startup
     * (before the observer was ready to receive onChange callbacks).  This closes
     * the small window between app launch and observer registration.
     */
    fun performInitialScan() {
        scope.launch {
            Log.d(TAG, "Performing initial scan for recent screenshots")
            scanLatestScreenshots(INITIAL_SCAN_WINDOW_SECONDS)
        }
    }

    fun clearProcessedUris() {
        synchronized(processedUris) {
            processedUris.clear()
            pendingUris.clear()
        }
    }

    // ---------------------------------------------------------------------
    // ContentObserver
    // ---------------------------------------------------------------------

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        Log.d(TAG, "onChange fired: uri=$uri")

        if (uri == null) {
            // No specific URI — fall back to scanning latest images.
            scope.launch { scanLatestScreenshots(FALLBACK_SCAN_WINDOW_SECONDS) }
            return
        }

        val id = try {
            ContentUris.parseId(uri)
        } catch (_: NumberFormatException) {
            Log.w(TAG, "Could not parse ID from uri=$uri, falling back to scan")
            scope.launch { scanLatestScreenshots(FALLBACK_SCAN_WINDOW_SECONDS) }
            return
        }

        val uriString = uri.toString()

        // Atomic check-and-add to prevent duplicate processing.
        synchronized(processedUris) {
            if (uriString in processedUris || uriString in pendingUris) {
                Log.d(TAG, "Already processed/pending, skipping: $uriString")
                return
            }
            pendingUris.add(uriString)
        }

        scope.launch {
            val detected = queryByIdWithRetry(id)
            if (detected) {
                addToProcessed(uriString)
                Log.d(TAG, "Successfully detected screenshot via URI: $uriString")
            } else {
                // Fallback: scan recent images in case MediaStore was slow to
                // populate the row columns (common on cold start).
                Log.w(TAG, "URI-based detection failed for id=$id, falling back to scan")
                scanLatestScreenshots(FALLBACK_SCAN_WINDOW_SECONDS)
            }
            synchronized(pendingUris) { pendingUris.remove(uriString) }
        }
    }

    // ---------------------------------------------------------------------
    // URI-based detection (primary)
    // ---------------------------------------------------------------------

    private suspend fun queryByIdWithRetry(id: Long): Boolean {
        for ((attempt, delayMs) in RETRY_DELAYS_MS.withIndex()) {
            if (queryById(id)) {
                Log.d(TAG, "queryById succeeded on attempt ${attempt + 1}")
                return true
            }
            Log.d(TAG, "queryById attempt ${attempt + 1} failed, waiting ${delayMs}ms")
            delay(delayMs)
        }
        // One final attempt after the last delay.
        if (queryById(id)) {
            Log.d(TAG, "queryById succeeded on final attempt")
            return true
        }
        Log.w(TAG, "queryById failed after ${RETRY_DELAYS_MS.size + 1} attempts")
        return false
    }

    /**
     * Queries MediaStore for a specific image ID.
     *
     * Returns `true` when the row is **fully ready** (columns populated, not pending)
     * — regardless of whether it is a screenshot or not — so the caller knows it can
     * stop retrying.  Returns `false` only when the row is not yet ready, signalling
     * the caller to retry.
     *
     * @return `true` if the row is ready (screenshot handled or non-screenshot
     *         confirmed), `false` if the row is not ready (retry needed).
     */
    private suspend fun queryById(id: Long): Boolean {
        val projection = arrayOf(
            MediaStore.Images.ImageColumns.DISPLAY_NAME,
            MediaStore.Images.ImageColumns.DATE_ADDED,
            MediaStore.Images.ImageColumns.RELATIVE_PATH,
            MediaStore.Images.ImageColumns.IS_PENDING
        )

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Images.ImageColumns._ID} = ?",
                arrayOf(id.toString()),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayName =
                        cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DISPLAY_NAME)) ?: ""
                    val relativePath =
                        cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.RELATIVE_PATH)) ?: ""
                    val dateAdded =
                        cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DATE_ADDED)) * 1000

                    // Check IS_PENDING flag (Android Q+).  When the row is still
                    // pending the columns may not be fully populated yet.
                    val isPending = try {
                        val idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.IS_PENDING)
                        if (idx != -1) cursor.getInt(idx) != 0 else false
                    } catch (_: Exception) {
                        false
                    }

                    if (isPending) {
                        Log.d(TAG, "queryById: id=$id is IS_PENDING, will retry")
                        return false
                    }

                    // Columns not yet populated — MediaStore is still indexing.
                    if (displayName.isBlank() || relativePath.isBlank()) {
                        Log.d(TAG, "queryById: id=$id has empty columns (name='$displayName', path='$relativePath'), will retry")
                        return false
                    }

                    val isScreenshot = displayName.contains("screenshot", ignoreCase = true) ||
                            relativePath.contains("screenshots", ignoreCase = true)

                    if (isScreenshot) {
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                        )
                        handleNewScreenshot(contentUri.toString(), displayName, dateAdded)
                        return true
                    }

                    // Row is fully populated but not a screenshot — stop retrying.
                    Log.d(TAG, "queryById: id=$id is not a screenshot (name='$displayName', path='$relativePath')")
                    return true
                }
                // Row doesn't exist yet — retry.
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query screenshot by ID: $id", e)
        }
        return false
    }

    // ---------------------------------------------------------------------
    // Scan-based detection (fallback / initial)
    // ---------------------------------------------------------------------

    /**
     * Scans the most recent images in MediaStore for screenshots that haven't
     * been processed yet.
     *
     * Used as:
     * - An initial scan on observer registration (catches screenshots taken
     *   during process startup).
     * - A fallback when URI-based [queryByIdWithRetry] fails (catches screenshots
     *   where MediaStore was slow to populate columns).
     *
     * @param windowSeconds How far back to look (based on DATE_ADDED).
     */
    private suspend fun scanLatestScreenshots(windowSeconds: Long) {
        val projection = arrayOf(
            MediaStore.Images.ImageColumns._ID,
            MediaStore.Images.ImageColumns.DISPLAY_NAME,
            MediaStore.Images.ImageColumns.DATE_ADDED,
            MediaStore.Images.ImageColumns.RELATIVE_PATH,
            MediaStore.Images.ImageColumns.IS_PENDING
        )

        val cutoff = (System.currentTimeMillis() / 1000) - windowSeconds

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Images.ImageColumns.DATE_ADDED} >= ?",
                arrayOf(cutoff.toString()),
                "${MediaStore.Images.ImageColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns._ID))
                    val displayName =
                        cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DISPLAY_NAME)) ?: ""
                    val relativePath =
                        cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.RELATIVE_PATH)) ?: ""
                    val dateAdded =
                        cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DATE_ADDED)) * 1000

                    // Skip rows that are still pending or have empty columns.
                    val isPending = try {
                        val idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.IS_PENDING)
                        if (idx != -1) cursor.getInt(idx) != 0 else false
                    } catch (_: Exception) {
                        false
                    }
                    if (isPending || displayName.isBlank() || relativePath.isBlank()) continue

                    val isScreenshot = displayName.contains("screenshot", ignoreCase = true) ||
                            relativePath.contains("screenshots", ignoreCase = true)
                    if (!isScreenshot) continue

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )
                    val uriString = contentUri.toString()

                    synchronized(processedUris) {
                        if (uriString in processedUris || uriString in pendingUris) continue
                        pendingUris.add(uriString)
                    }

                    Log.d(TAG, "scanLatest: found screenshot: $uriString")
                    handleNewScreenshot(uriString, displayName, dateAdded)
                    addToProcessed(uriString)
                    synchronized(pendingUris) { pendingUris.remove(uriString) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "scanLatestScreenshots failed", e)
        }
    }

    // ---------------------------------------------------------------------
    // Screenshot handling
    // ---------------------------------------------------------------------

    /**
     * Inserts the screenshot into the database and shows a notification.
     *
     * This is a suspend function (not fire-and-forget) so callers can wait for
     * processing to complete before marking the URI as processed.
     */
    private suspend fun handleNewScreenshot(uriString: String, fileName: String, createdAt: Long) {
        val existing = repository.getScreenshotByUri(uriString)
        if (existing != null) {
            Log.d(TAG, "handleNewScreenshot: already in DB, skipping: $uriString")
            return
        }

        val uri = Uri.parse(uriString)
        val fileSize = try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                fd.statSize
            } ?: 0L
        } catch (_: Exception) {
            0L
        }

        val isAutoArchive = settingsRepository.isAutoArchiveEnabled()
        val entity = ScreenshotEntity(
            uri = uriString,
            fileName = fileName,
            createdAt = createdAt,
            fileSize = fileSize,
            archived = isAutoArchive,
            deleted = false
        )
        repository.insertScreenshot(entity)
        notificationManager.showScreenshotNotification(uriString, isAutoArchive)
        Log.d(TAG, "handleNewScreenshot: inserted and notified for $uriString")
    }

    // ---------------------------------------------------------------------
    // Dedup helpers
    // ---------------------------------------------------------------------

    private fun addToProcessed(uriString: String) {
        synchronized(processedUris) {
            if (processedUris.size >= MAX_PROCESSED_URIS) {
                // Evict oldest ~25 % to prevent unbounded growth.
                val toRemove = processedUris.take(MAX_PROCESSED_URIS / 4)
                processedUris.removeAll(toRemove.toSet())
            }
            processedUris.add(uriString)
        }
    }
}