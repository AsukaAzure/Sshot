package dev.sj010.ssjanitor.ui.overlay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import dev.sj010.ssjanitor.core.constants.AppConstants
import dev.sj010.ssjanitor.data.db.AppDatabase
import dev.sj010.ssjanitor.data.repository.DeleteResult
import dev.sj010.ssjanitor.data.repository.ScreenshotRepository
import dev.sj010.ssjanitor.ui.theme.SshotTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

class ScreenshotOverlayActivity : ComponentActivity() {

    private lateinit var repository: ScreenshotRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val database = AppDatabase.getDatabase(applicationContext)
        repository = ScreenshotRepository(database.screenshotDao())
        val settingsRepository = dev.sj010.ssjanitor.data.repository.SettingsRepository(applicationContext)
        val isRightSide = settingsRepository.isOverlayOnRightSide()

        val uriString = intent.getStringExtra(AppConstants.EXTRA_SCREENSHOT_URI) ?: run {
            finish()
            return
        }

        setContent {
            SshotTheme {
                OverlayContent(
                    uriString = uriString,
                    onAction = { finish() },
                    repository = repository,
                    isRightSide = isRightSide
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun OverlayContent(
        uriString: String,
        onAction: () -> Unit,
        repository: ScreenshotRepository,
        isRightSide: Boolean
    ) {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        var showDatePicker by remember { mutableStateOf(false) }
        var showTimePicker by remember { mutableStateOf(false) }
        val datePickerState = rememberDatePickerState()
        val timePickerState = rememberTimePickerState()
        
        var isVisible by remember { mutableStateOf(false) }
        var actionTaken by remember { mutableStateOf(false) }

        val intentSenderLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                scope.launch {
                    repository.markAsDeleted(listOf(uriString))
                    isVisible = false
                    delay(300)
                    onAction()
                }
            }
        }
        
        val dismissOverlay = {
            scope.launch {
                if (!actionTaken) {
                    repository.keepScreenshot(uriString)
                }
                isVisible = false
                delay(300)
                onAction()
            }
        }

        LaunchedEffect(Unit) {
            isVisible = true
        }

        BackHandler {
            dismissOverlay()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { dismissOverlay() } // Dismiss on background click
                .background(Color.Black.copy(alpha = 0.2f)),
            contentAlignment = if (isRightSide) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInHorizontally(
                    initialOffsetX = { if (isRightSide) it else -it },
                    animationSpec = tween(durationMillis = 400)
                ) + fadeIn(),
                exit = slideOutHorizontally(
                    targetOffsetX = { if (isRightSide) it else -it },
                    animationSpec = tween(durationMillis = 300)
                ) + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxHeight(0.5f)
                        .width(280.dp)
                        .clickable(enabled = false) { }, // Prevent clicks from passing through
                    shape = if (isRightSide) {
                        RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                    } else {
                        RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "Screenshot Captured",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 28.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Schedule for deletion",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Row 1: Tonight & Tom
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                ScheduleButtonSmall("Tonight", Icons.Default.Nightlight) {
                                    scope.launch {
                                        actionTaken = true
                                        repository.scheduleDeletion(context, uriString, getTonightTime())
                                        dismissOverlay()
                                    }
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                ScheduleButtonSmall("Tom", Icons.Default.WbSunny) {
                                    scope.launch {
                                        actionTaken = true
                                        repository.scheduleDeletion(context, uriString, getTomorrowTime())
                                        dismissOverlay()
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))

                        // Row 2: Weekend & Custom
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                ScheduleButtonSmall("Weekend", Icons.Default.Weekend) {
                                    scope.launch {
                                        actionTaken = true
                                        repository.scheduleDeletion(context, uriString, getWeekendTime())
                                        dismissOverlay()
                                    }
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                ScheduleButtonSmall("Custom", Icons.Default.CalendarMonth) {
                                    showDatePicker = true
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

                        Button(
                            onClick = {
                                scope.launch {
                                    actionTaken = true
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/*"
                                        putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Screenshot"))
                                    repository.scheduleDeletion(context, uriString, getTonightTime(), true)
                                    dismissOverlay()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share & Delete Tonight", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        actionTaken = true
                                        repository.keepScreenshot(uriString)
                                        dismissOverlay()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Text("Keep", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        actionTaken = true
                                        val result = repository.deleteScreenshot(context, uriString)
                                        when (result) {
                                            is DeleteResult.RequiresPermission -> {
                                                intentSenderLauncher.launch(
                                                    IntentSenderRequest.Builder(result.intentSender).build()
                                                )
                                            }
                                            is DeleteResult.Success -> {
                                                repository.markAsDeleted(listOf(uriString))
                                                dismissOverlay()
                                            }
                                            is DeleteResult.Failed -> {
                                                android.util.Log.e("ScreenshotOverlay", "Delete failed", result.error)
                                                dismissOverlay()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Text("Delete Now", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            if (datePickerState.selectedDateMillis != null) {
                                showDatePicker = false
                                showTimePicker = true
                            }
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            if (showTimePicker) {
                AlertDialog(
                    onDismissRequest = { showTimePicker = false },
                    title = { Text("Select Time") },
                    text = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TimePicker(state = timePickerState)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { dateMillis ->
                                val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                                calendar.timeInMillis = dateMillis
                                val year = calendar.get(Calendar.YEAR)
                                val month = calendar.get(Calendar.MONTH)
                                val day = calendar.get(Calendar.DAY_OF_MONTH)

                                val localCalendar = Calendar.getInstance()
                                localCalendar.set(year, month, day, timePickerState.hour, timePickerState.minute)
                                localCalendar.set(Calendar.SECOND, 0)
                                localCalendar.set(Calendar.MILLISECOND, 0)

                                android.util.Log.d("ScreenshotOverlay", "Selected custom time: ${localCalendar.time}")

                                scope.launch {
                                    actionTaken = true
                                    repository.scheduleDeletion(context, uriString, localCalendar.timeInMillis)
                                    showTimePicker = false
                                    dismissOverlay()
                                }
                            }
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }

    @Composable
    fun ScheduleButtonSmall(label: String, icon: ImageVector, onClick: () -> Unit) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }

    private fun getTonightTime(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 22)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        if (System.currentTimeMillis() > calendar.timeInMillis) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    private fun getTomorrowTime(): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 22)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun getWeekendTime(): Long {
        val calendar = Calendar.getInstance()
        // Find next Saturday
        do {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        } while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY)
        
        calendar.set(Calendar.HOUR_OF_DAY, 10)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
