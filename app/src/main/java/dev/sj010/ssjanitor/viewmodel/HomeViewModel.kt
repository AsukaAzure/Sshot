package dev.sj010.ssjanitor.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import dev.sj010.ssjanitor.data.db.entity.ScreenshotEntity
import dev.sj010.ssjanitor.data.repository.ScreenshotRepository
import dev.sj010.ssjanitor.data.repository.SettingsRepository
import dev.sj010.ssjanitor.network.UpdateManager
import dev.sj010.ssjanitor.worker.ScreenshotCleanupWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class HomeUiState(
    val screenshots: List<ScreenshotEntity> = emptyList(),
    val totalCount: Int = 0,
    val archivedCount: Int = 0,
    val keptCount: Int = 0,
    val deletedCount: Int = 0,
    val deletedBytes: Long = 0L,
    val pendingCount: Int = 0,
    val isAutoArchiveEnabled: Boolean = false,
    val isCleanupPaused: Boolean = false,
    val isOverlayOnRightSide: Boolean = false,
    val preset1Minutes: Int = 60,
    val preset2Minutes: Int = 720,
    val preset3Minutes: Int = 4320,
    val latestVersion: String? = null
)

class HomeViewModel(
    private val repository: ScreenshotRepository,
    private val settingsRepository: SettingsRepository,
    private val workManager: WorkManager
) : ViewModel() {

    private val _isAutoArchiveEnabled = MutableStateFlow(settingsRepository.isAutoArchiveEnabled())
    val isAutoArchiveEnabled = _isAutoArchiveEnabled.asStateFlow()

    private val _isCleanupPaused = MutableStateFlow(settingsRepository.isCleanupPaused())
    val isCleanupPaused = _isCleanupPaused.asStateFlow()

    private val _isOverlayOnRightSide = MutableStateFlow(settingsRepository.isOverlayOnRightSide())
    val isOverlayOnRightSide = _isOverlayOnRightSide.asStateFlow()

    private val _preset1Minutes = MutableStateFlow(settingsRepository.getPreset1Minutes())
    val preset1Minutes = _preset1Minutes.asStateFlow()

    private val _preset2Minutes = MutableStateFlow(settingsRepository.getPreset2Minutes())
    val preset2Minutes = _preset2Minutes.asStateFlow()

    private val _preset3Minutes = MutableStateFlow(settingsRepository.getPreset3Minutes())
    val preset3Minutes = _preset3Minutes.asStateFlow()

    private val _latestVersion = MutableStateFlow<String?>(null)
    val latestVersion = _latestVersion.asStateFlow()

    init {
        // Run reconciliation when ViewModel is created (app start)
        // We use a separate context/scope if needed, but viewModelScope is fine.
        // But we need a Context for reconciliation. We can't easily get it here unless we pass it.
        // Actually, ScreenshotRepository might need Context for reconcileDatabase.
    }

    val uiState: StateFlow<HomeUiState> = combine(
        repository.allScreenshots,
        _isAutoArchiveEnabled,
        _isCleanupPaused,
        _isOverlayOnRightSide,
        _preset1Minutes,
        _preset2Minutes,
        _preset3Minutes,
        _latestVersion
    ) { args ->
        val screenshots = args[0] as List<ScreenshotEntity>
        val isAutoEnabled = args[1] as Boolean
        val isPaused = args[2] as Boolean
        val isOverlayRight = args[3] as Boolean
        val p1 = args[4] as Int
        val p2 = args[5] as Int
        val p3 = args[6] as Int
        val latestVer = args[7] as String?

        var archived = 0
        var kept = 0
        var deleted = 0
        var deletedBytes = 0L
        var pending = 0
        screenshots.forEach {
            when {
                it.deleted -> {
                    deleted++
                    deletedBytes += it.fileSize
                }
                it.kept -> kept++
                it.archived -> archived++
                else -> pending++
            }
        }
        HomeUiState(
            screenshots = screenshots,
            totalCount = screenshots.size,
            archivedCount = archived,
            keptCount = kept,
            deletedCount = deleted,
            deletedBytes = deletedBytes,
            pendingCount = pending,
            isAutoArchiveEnabled = isAutoEnabled,
            isCleanupPaused = isPaused,
            isOverlayOnRightSide = isOverlayRight,
            preset1Minutes = p1,
            preset2Minutes = p2,
            preset3Minutes = p3,
            latestVersion = latestVer
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun toggleAutoArchive() {
        val newValue = !settingsRepository.isAutoArchiveEnabled()
        settingsRepository.setAutoArchiveEnabled(newValue)
        _isAutoArchiveEnabled.value = newValue
        // We might need to refresh uiState if it doesn't observe settingsRepository
        // Actually uiState map above re-reads it, but it might not trigger on change.
        // For simplicity, we can just rely on isAutoArchiveEnabled flow for UI feedback.
    }

    fun toggleCleanupPause(context: Context) {
        val newValue = !settingsRepository.isCleanupPaused()
        settingsRepository.setCleanupPaused(newValue)
        _isCleanupPaused.value = newValue
        if (!newValue) {
            // If resumed, run cleanup immediately
            runCleanupNow(context)
        }
    }

    fun toggleOverlaySide() {
        val newValue = !settingsRepository.isOverlayOnRightSide()
        settingsRepository.setOverlayOnRightSide(newValue)
        _isOverlayOnRightSide.value = newValue
    }

    fun updatePreset1Minutes(minutes: Int) {
        settingsRepository.setPreset1Minutes(minutes)
        _preset1Minutes.value = minutes
    }

    fun updatePreset2Minutes(minutes: Int) {
        settingsRepository.setPreset2Minutes(minutes)
        _preset2Minutes.value = minutes
    }

    fun updatePreset3Minutes(minutes: Int) {
        settingsRepository.setPreset3Minutes(minutes)
        _preset3Minutes.value = minutes
    }

    fun dismissUpdateDialog() {
        _latestVersion.value = null
    }

    fun checkForUpdates(context: Context) {
        viewModelScope.launch {
            val latest = UpdateManager(context).checkForUpdates()
            _latestVersion.value = latest
        }
    }

    fun archiveScreenshot(uri: String) {
        viewModelScope.launch {
            repository.archiveScreenshot(uri)
        }
    }

    fun keepScreenshot(uri: String) {
        viewModelScope.launch {
            repository.keepScreenshot(uri)
        }
    }

    fun reconcileDatabase(context: Context) {
        viewModelScope.launch {
            repository.reconcileDatabase(context)
        }
    }

    private val _events = kotlinx.coroutines.flow.MutableSharedFlow<HomeEvent>()
    val events: kotlinx.coroutines.flow.SharedFlow<HomeEvent> = _events.asSharedFlow()

    private var pendingUrisToDelete: List<String> = emptyList()

    fun deleteScreenshot(context: Context, uri: String) {
        viewModelScope.launch {
            pendingUrisToDelete = listOf(uri)
            val result = repository.deleteScreenshots(context, pendingUrisToDelete)
                if (result is dev.sj010.ssjanitor.data.repository.DeleteResult.RequiresPermission) {
                _events.emit(HomeEvent.RequestDeletePermission(result.intentSender))
            }
        }
    }

    fun runCleanupNow(context: Context) {
        viewModelScope.launch {
            // Cleanup targets archived screenshots OR those whose scheduled time has passed
            val screenshotsToCleanup = repository.getScreenshotsForCleanup()
            if (screenshotsToCleanup.isNotEmpty()) {
                pendingUrisToDelete = screenshotsToCleanup.map { it.uri }
                val result = repository.deleteScreenshots(context, pendingUrisToDelete)
                if (result is dev.sj010.ssjanitor.data.repository.DeleteResult.RequiresPermission) {
                    _events.emit(HomeEvent.RequestDeletePermission(result.intentSender))
                }
            }
        }
    }

    fun onDeletePermissionGranted() {
        viewModelScope.launch {
            repository.markAsDeleted(pendingUrisToDelete)
            pendingUrisToDelete = emptyList()
        }
    }

    fun onDeletePermissionDenied() {
        pendingUrisToDelete = emptyList()
    }
}

sealed class HomeEvent {
    class RequestDeletePermission(val intentSender: android.content.IntentSender) : HomeEvent()
}

class HomeViewModelFactory(
    private val repository: ScreenshotRepository,
    private val settingsRepository: SettingsRepository,
    private val workManager: WorkManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(repository, settingsRepository, workManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
