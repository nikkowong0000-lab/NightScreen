package com.yourcompany.screenlight

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.yourcompany.screenlight.ui.theme.ScreenlightTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import java.util.Arrays

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize AdMob
        MobileAds.initialize(this) { initializationStatus ->
            val statusMap = initializationStatus.adapterStatusMap
            for ((adapterClass, status) in statusMap) {
                Log.d("AdMob", "Adapter name: $adapterClass, Description: ${status.description}, Latency: ${status.latency}")
            }
        }

        // Set test device ID (Emulator is added by default)
        // Keep this for development to avoid policy violations on your own devices
        val testDeviceIds = Arrays.asList(AdRequest.DEVICE_ID_EMULATOR)
        val configuration = RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build()
        MobileAds.setRequestConfiguration(configuration)
        
        setContent {
            ScreenlightTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun AdmobBanner() {
    AndroidView(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                // Real Ad Unit ID
                adUnitId = "ca-app-pub-9908898567152436/9985104488" 
                
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        Log.d("AdMob", "Ad loaded successfully")
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        Log.e("AdMob", "Ad failed to load: ${adError.message}, Code: ${adError.code}")
                    }

                    override fun onAdOpened() {
                        Log.d("AdMob", "Ad opened")
                    }

                    override fun onAdClicked() {
                        Log.d("AdMob", "Ad clicked")
                    }

                    override fun onAdClosed() {
                        Log.d("AdMob", "Ad closed")
                    }
                }
                
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    // State from DataStore
    val savedAlpha by context.dataStore.data
        .map { it[AppSettings.ALPHA_KEY] ?: 0.3f }
        .collectAsState(initial = 0.3f)
        
    val savedIsRunning by context.dataStore.data
        .map { it[AppSettings.IS_RUNNING_KEY] ?: false }
        .collectAsState(initial = false)

    val savedIsBlueLightEnabled by context.dataStore.data
        .map { it[AppSettings.IS_BLUE_LIGHT_KEY] ?: false }
        .collectAsState(initial = false)
        
    val savedWarmth by context.dataStore.data
        .map { it[AppSettings.WARMTH_KEY] ?: 0.5f }
        .collectAsState(initial = 0.5f)

    // Schedule State
    val savedScheduleEnabled by context.dataStore.data
        .map { it[AppSettings.SCHEDULE_ENABLED_KEY] ?: false }
        .collectAsState(initial = false)
    val savedStartHour by context.dataStore.data
        .map { it[AppSettings.START_HOUR_KEY] ?: 22 }
        .collectAsState(initial = 22)
    val savedStartMinute by context.dataStore.data
        .map { it[AppSettings.START_MINUTE_KEY] ?: 0 }
        .collectAsState(initial = 0)
    val savedEndHour by context.dataStore.data
        .map { it[AppSettings.END_HOUR_KEY] ?: 7 }
        .collectAsState(initial = 7)
    val savedEndMinute by context.dataStore.data
        .map { it[AppSettings.END_MINUTE_KEY] ?: 0 }
        .collectAsState(initial = 0)

    // Local State
    var sliderPosition by remember(savedAlpha) { mutableFloatStateOf(savedAlpha) }
    var isServiceRunning by remember(savedIsRunning) { mutableStateOf(savedIsRunning) }
    var isBlueLightEnabled by remember(savedIsBlueLightEnabled) { mutableStateOf(savedIsBlueLightEnabled) }
    var warmthPosition by remember(savedWarmth) { mutableFloatStateOf(savedWarmth) }

    // Schedule Local State
    var isScheduleEnabled by remember(savedScheduleEnabled) { mutableStateOf(savedScheduleEnabled) }
    var startHour by remember(savedStartHour) { mutableIntStateOf(savedStartHour) }
    var startMinute by remember(savedStartMinute) { mutableIntStateOf(savedStartMinute) }
    var endHour by remember(savedEndHour) { mutableIntStateOf(savedEndHour) }
    var endMinute by remember(savedEndMinute) { mutableIntStateOf(savedEndMinute) }
    
    // Time Picker Dialog State
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    
    if (showStartTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = startHour,
            initialMinute = startMinute
        )
        DatePickerDialog(
            onDismissRequest = { showStartTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startHour = timePickerState.hour
                    startMinute = timePickerState.minute
                    showStartTimePicker = false
                    // Save and schedule
                    scope.launch {
                        context.dataStore.edit { 
                            it[AppSettings.START_HOUR_KEY] = startHour
                            it[AppSettings.START_MINUTE_KEY] = startMinute
                        }
                        if (isScheduleEnabled) {
                            ScheduleReceiver.scheduleNextAlarm(context, true)
                        }
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartTimePicker = false }) { Text("Cancel") }
            }
        ) {
            TimePicker(state = timePickerState)
        }
    }

    if (showEndTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = endHour,
            initialMinute = endMinute
        )
        DatePickerDialog(
            onDismissRequest = { showEndTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endHour = timePickerState.hour
                    endMinute = timePickerState.minute
                    showEndTimePicker = false
                    // Save and schedule
                    scope.launch {
                        context.dataStore.edit {
                            it[AppSettings.END_HOUR_KEY] = endHour
                            it[AppSettings.END_MINUTE_KEY] = endMinute
                        }
                        if (isScheduleEnabled) {
                            ScheduleReceiver.scheduleNextAlarm(context, false)
                        }
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndTimePicker = false }) { Text("Cancel") }
            }
        ) {
            TimePicker(state = timePickerState)
        }
    }


    val maxAlpha = 0.8f
    val minAlpha = 0.0f

    // 辅助函数：仅更新服务（轻量级，用于拖动时）
    fun updateServiceState(alpha: Float, blueLight: Boolean, warmth: Float) {
        if (isServiceRunning) {
            val intent = Intent(context, ScreenFilterService::class.java).apply {
                putExtra(ScreenFilterService.EXTRA_ALPHA, alpha)
                putExtra(ScreenFilterService.EXTRA_BLUE_LIGHT, blueLight)
                putExtra(ScreenFilterService.EXTRA_WARMTH, warmth)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    // 辅助函数：保存到 DataStore（重量级，用于松手后）
    fun saveSettings(alpha: Float? = null, blueLight: Boolean? = null, warmth: Float? = null, isRunning: Boolean? = null) {
        scope.launch {
            context.dataStore.edit { preferences ->
                if (alpha != null) preferences[AppSettings.ALPHA_KEY] = alpha
                if (blueLight != null) preferences[AppSettings.IS_BLUE_LIGHT_KEY] = blueLight
                if (warmth != null) preferences[AppSettings.WARMTH_KEY] = warmth
                if (isRunning != null) preferences[AppSettings.IS_RUNNING_KEY] = isRunning
            }
        }
    }

    // Permission Launchers
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }
    
    fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    
    // Alarm Permission Launcher
    val alarmPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
         // Check if permission granted
         val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
              scope.launch {
                  context.dataStore.edit { it[AppSettings.SCHEDULE_ENABLED_KEY] = true }
                  isScheduleEnabled = true
                  ScheduleReceiver.scheduleNextAlarm(context, true)
                  ScheduleReceiver.scheduleNextAlarm(context, false)
              }
         }
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(context)) {
            // Permission granted, start service
            // 权限获取成功后，立即更新状态，isServiceRunning 设为 true
            isServiceRunning = true
            
            // 权限获取后的逻辑：如果是首次启动，且没有开启任何效果，默认开启蓝光
            if (!isBlueLightEnabled && sliderPosition < 0.1f) {
                isBlueLightEnabled = true
                saveSettings(blueLight = true)
            }
            updateServiceState(sliderPosition, isBlueLightEnabled, warmthPosition)
            saveSettings(isRunning = true)
        }
    }

    fun startFilterService() {
        if (Settings.canDrawOverlays(context)) {
            checkAndRequestNotificationPermission()
            isServiceRunning = true // 立即在 UI 上反映开启状态
            
            // 优化逻辑：如果滤镜完全透明，则默认开启蓝光以提供反馈
            if (sliderPosition < 0.1f && !isBlueLightEnabled) {
                isBlueLightEnabled = true
                saveSettings(blueLight = true)
            }
            
            updateServiceState(sliderPosition, isBlueLightEnabled, warmthPosition)
            saveSettings(isRunning = true)
        } else {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            overlayPermissionLauncher.launch(intent)
        }
    }

    fun stopFilterService() {
        isServiceRunning = false // 立即在 UI 上反映关闭状态
        val intent = Intent(context, ScreenFilterService::class.java).apply {
            action = ScreenFilterService.ACTION_STOP
        }
        context.startService(intent)
        saveSettings(isRunning = false)
    }

    // Auto-start
    val permissionsGranted = Settings.canDrawOverlays(context)
    LaunchedEffect(permissionsGranted, savedIsBlueLightEnabled) {
        if (permissionsGranted) {
            // Start service with current saved settings
            updateServiceState(savedAlpha, savedIsBlueLightEnabled, savedWarmth)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("NightScreen", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                if (!isLandscape) {
                    Surface(
                        modifier = Modifier
                            .size(100.dp)
                            .padding(bottom = 32.dp)
                            .align(Alignment.CenterHorizontally),
                        shape = RoundedCornerShape(20.dp),
                        shadowElevation = 8.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.screenlight_app_icon),
                            contentDescription = "App Icon",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Main Control Card
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Enable Filter", style = MaterialTheme.typography.titleMedium)
                            }
                            Switch(
                                checked = isServiceRunning,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        startFilterService()
                                    } else {
                                        stopFilterService()
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(24.dp))

                        // Brightness Slider
                        val linearProgress = (maxAlpha - sliderPosition) / (maxAlpha - minAlpha)
                        val displayBrightness = sqrt(linearProgress.coerceAtLeast(0f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Brightness", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${(displayBrightness * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Slider(
                            value = displayBrightness,
                            onValueChange = { newBrightness ->
                                val linearP = newBrightness * newBrightness
                                val newAlpha = maxAlpha - (linearP * (maxAlpha - minAlpha))
                                sliderPosition = newAlpha
                                // 实时更新 Service，但不保存到 DataStore
                                updateServiceState(newAlpha, isBlueLightEnabled, warmthPosition)
                            },
                            onValueChangeFinished = {
                                // 拖动结束后保存
                                saveSettings(alpha = sliderPosition)
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.height(40.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(
                                imageVector = Icons.Default.BrightnessMedium,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Default.WbSunny,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Blue Light Card
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.screenlight_app_icon),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Blue Light Filter", style = MaterialTheme.typography.titleMedium)
                            }
                            Switch(
                                checked = isBlueLightEnabled,
                                onCheckedChange = { isChecked ->
                                    isBlueLightEnabled = isChecked
                                    // 逻辑优化：如果开启蓝光，且总开关没开，则自动开启总开关
                                    if (isChecked && !isServiceRunning) {
                                        startFilterService()
                                    }
                                    updateServiceState(sliderPosition, isChecked, warmthPosition)
                                    saveSettings(blueLight = isChecked)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.secondary,
                                    checkedTrackColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            )
                        }

                        AnimatedVisibility(visible = isBlueLightEnabled) {
                            Column {
                                Spacer(modifier = Modifier.height(24.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Temperature", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "${(warmthPosition * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                Slider(
                                    value = warmthPosition,
                                    onValueChange = { newWarmth ->
                                        warmthPosition = newWarmth
                                        updateServiceState(sliderPosition, isBlueLightEnabled, newWarmth)
                                    },
                                    onValueChangeFinished = {
                                        saveSettings(warmth = warmthPosition)
                                    },
                                    valueRange = 0f..1f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.secondary,
                                        activeTrackColor = MaterialTheme.colorScheme.secondary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    modifier = Modifier.height(40.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Cool",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "Warm",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Auto-Schedule Card
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Auto-Schedule", style = MaterialTheme.typography.titleMedium)
                            }
                            Switch(
                                checked = isScheduleEnabled,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        val alarmManager =
                                            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                                            // Request permission
                                            val intent =
                                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                            alarmPermissionLauncher.launch(intent)
                                        } else {
                                            isScheduleEnabled = true
                                            scope.launch {
                                                context.dataStore.edit {
                                                    it[AppSettings.SCHEDULE_ENABLED_KEY] = true
                                                }
                                                ScheduleReceiver.scheduleNextAlarm(context, true)
                                                ScheduleReceiver.scheduleNextAlarm(context, false)
                                            }
                                        }
                                    } else {
                                        isScheduleEnabled = false
                                        scope.launch {
                                            context.dataStore.edit {
                                                it[AppSettings.SCHEDULE_ENABLED_KEY] = false
                                            }
                                            ScheduleReceiver.cancelAlarms(context)
                                        }
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.tertiary,
                                    checkedTrackColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            )
                        }

                        AnimatedVisibility(visible = isScheduleEnabled) {
                            Column {
                                Spacer(modifier = Modifier.height(24.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Start Time
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { showStartTimePicker = true }
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("Start", style = MaterialTheme.typography.labelMedium)
                                        Text(
                                            text = String.format(
                                                "%02d:%02d",
                                                startHour,
                                                startMinute
                                            ),
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    // End Time
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { showEndTimePicker = true }
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("End", style = MaterialTheme.typography.labelMedium)
                                        Text(
                                            text = String.format(
                                                "%02d:%02d",
                                                endHour,
                                                endMinute
                                            ),
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // AdMob Banner
            AdmobBanner()
        }
    }
}
