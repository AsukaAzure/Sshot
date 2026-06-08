# Features

## Screenshot Detection

Detect newly created screenshots using `MediaStore` and `ContentObserver`.

- Supports Android 14+ scoped storage model.
- Event-driven architecture — no continuous polling.
- Filters images by screenshot naming/path conventions.

**Implementation:** `observer/ScreenshotContentObserver.kt` · `observer/ScreenshotDetector.kt`

---

## Action Notifications

After a screenshot is detected, the user receives a notification with quick actions:

| Action | Effect |
|---|---|
| **Archive** | Marks screenshot for automatic cleanup by the Janitor |
| **Keep** | Preserves screenshot — excluded from cleanup |
| **Delete** | Immediately deletes from device storage |

Notifications are intentionally dismissible and non-intrusive.

**Implementation:** `notifications/ScreenshotNotificationManager.kt` · `notifications/NotificationActionReceiver.kt`

---

## Archive System

The app stores metadata locally using Room instead of physically moving files.

- Archived screenshots are marked for automatic cleanup.
- Screenshots marked as "Keep" are preserved.
- The Janitor processes archived entries on its next scheduled run.

**Implementation:** `data/repository/ScreenshotRepository.kt`

---

## Auto-Archive Mode

For power users who want to cleanup everything by default.

| Aspect | Detail |
|---|---|
| **Toggle** | Long-press the "Archived" card on the Home screen |
| **Behavior** | Every new screenshot is automatically marked as "Archived" upon detection |
| **Indicator** | An "AUTO" badge appears on the Archived stats card when active |
| **Notifications** | Reflect auto-archived status — offers "Keep" or "Delete Now" |

**Implementation:** `ui/screens/home/` · `data/repository/SettingsRepository.kt`

---

## Automatic Cleanup

A scheduled WorkManager worker removes archived screenshots from device storage.

- Runs daily via `WorkManager` periodic task.
- Queries Room for archived screenshots.
- Deletes from `MediaStore` (Scoped Storage compliant).
- Updates database state on completion.
- Includes retry handling for failed deletions.
- Also identifies unarchived screenshots beyond retention period for cleanup recommendations.

**Implementation:** `worker/ScreenshotCleanupWorker.kt`
