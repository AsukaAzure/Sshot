package dev.sj010.ssjanitor.ui.screens.home

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.sj010.ssjanitor.core.constants.AppConstants
import dev.sj010.ssjanitor.viewmodel.HomeEvent
import dev.sj010.ssjanitor.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val nextCleanupTime by viewModel.nextCleanupTimeMillis.collectAsState()

    // ── Update Check ────────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        viewModel.checkForUpdates(context)
    }

    if (uiState.latestVersion != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog() },
            title = { Text("Update Available") },
            text = { Text("A new version (${uiState.latestVersion}) is available on GitHub. Would you like to update now?") },
            confirmButton = {
                TextButton(onClick = {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        Uri.parse(AppConstants.GITHUB_REPO_URL + "/releases/latest")
                    )
                    context.startActivity(intent)
                    viewModel.dismissUpdateDialog()
                }) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                    Text("Later")
                }
            }
        )
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    var hasStoragePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isAllFilesManager by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.os.Environment.isExternalStorageManager()
            } else true
        )
    }

    var isBatteryOptDisabled by remember {
        mutableStateOf(
            (context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true
        )
    }

    var canDrawOverlays by remember {
        mutableStateOf(android.provider.Settings.canDrawOverlays(context))
    }

    // ── Reconciliation ───────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        viewModel.reconcileDatabase(context)
    }

    // ── Lifecycle Observer (Resume & Permissions) ────────────────────────────
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.reconcileDatabase(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    isAllFilesManager = android.os.Environment.isExternalStorageManager()
                }
                isBatteryOptDisabled =
                    (context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager)
                        ?.isIgnoringBatteryOptimizations(context.packageName) == true
                canDrawOverlays = android.provider.Settings.canDrawOverlays(context)
            }
        })
    }

    // ── Permission launcher ──────────────────────────────────────────────────
    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onDeletePermissionGranted()
        } else {
            viewModel.onDeletePermissionDenied()
        }
    }

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.RequestDeletePermission -> {
                    intentSenderLauncher.launch(
                        IntentSenderRequest.Builder(event.intentSender).build()
                    )
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission =
                permissions[Manifest.permission.POST_NOTIFICATIONS] ?: hasNotificationPermission
        }
        hasStoragePermission =
            permissions[Manifest.permission.READ_MEDIA_IMAGES] ?: hasStoragePermission
    }

    val batteryOptLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isBatteryOptDisabled =
            (context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        canDrawOverlays = android.provider.Settings.canDrawOverlays(context)
    }

    // ── Scroll-aware TopAppBar ────────────────────────────────────────────────
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row {
                            Text(
                                text = "Sshot",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        HomeContent(
            innerPadding = innerPadding,
            uiState = uiState,
            nextCleanupTime = nextCleanupTime,
            hasNotificationPermission = hasNotificationPermission,
            hasStoragePermission = hasStoragePermission,
            isAllFilesManager = isAllFilesManager,
            isBatteryOptDisabled = isBatteryOptDisabled,
            canDrawOverlays = canDrawOverlays,
            onRequestPermissions = {
                val list = mutableListOf<String>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                    list.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (!hasStoragePermission) {
                    list.add(Manifest.permission.READ_MEDIA_IMAGES)
                }
                if (list.isNotEmpty()) {
                    permissionLauncher.launch(list.toTypedArray())
                }
            },
            onRequestAllFilesAccess = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            },
            onRequestDisableBatteryOpt = {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                ).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                batteryOptLauncher.launch(intent)
            },
            onRequestOverlayPermission = {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                overlayPermissionLauncher.launch(intent)
            },
            onRunCleanup = { viewModel.runCleanupNow(context) },
            onTogglePause = { viewModel.toggleCleanupPause() },
            onReschedule = { hour, minute -> viewModel.rescheduleCleanup(hour, minute, context) },
            onArchive = { viewModel.archiveScreenshot(it) },
            onKeep = { viewModel.keepScreenshot(it) },
            onDelete = { viewModel.deleteScreenshot(context, it) },
            onToggleAutoArchive = {
                viewModel.toggleAutoArchive()
                val isEnabled = viewModel.isAutoArchiveEnabled.value
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = if (isEnabled) "Auto-Archive Enabled" else "Auto-Archive Disabled"
                    )
                }
            }
        )
    }
}
