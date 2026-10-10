package com.yourcompany.screenlight

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.LinearLayout
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

class ScreenFilterService : Service() {

    private lateinit var windowManager: WindowManager
    
    // Containter for layered views
    private lateinit var overlayContainer: FrameLayout
    private lateinit var dimmerView: View // Controls Brightness
    private lateinit var filterView: View // Controls Blue Light
    
    private var isOverlayAdded = false
    private var currentAlpha = 0.3f
    private val maxAlpha = 0.8f
    private val minAlpha = 0.0f
    
    // Blue light filter variables
    private var isBlueLightFilterEnabled = false
    private var warmthIntensity = 0.5f 
    
    // Floating ball variables
    private lateinit var floatingBallView: View
    private var isFloatingBallAdded = false
    private lateinit var floatingBallParams: WindowManager.LayoutParams
    
    // Control panel variables
    private lateinit var controlPanelView: View
    private var isControlPanelAdded = false
    private lateinit var controlPanelParams: WindowManager.LayoutParams
    private val hideControlPanelHandler = Handler(Looper.getMainLooper())
    private val hideControlPanelRunnable = Runnable { removeControlPanel() }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_STOP) {
                stopSelf()
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "ScreenFilterChannel"
        const val NOTIFICATION_ID = 1
        const val EXTRA_ALPHA = "extra_alpha"
        const val EXTRA_BLUE_LIGHT = "extra_blue_light"
        const val EXTRA_WARMTH = "extra_warmth"
        const val ACTION_UPDATE = "action_update"
        const val ACTION_STOP = "action_stop"
        const val ACTION_TOGGLE = "action_toggle"
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        ContextCompat.registerReceiver(
            this,
            notificationReceiver,
            IntentFilter(ACTION_STOP),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // 初始化分层视图
        createOverlayLayers()

        createFloatingBall()
        createControlPanel()
    }

    private fun createOverlayLayers() {
        // 1. 容器
        overlayContainer = FrameLayout(this)

        // 2. 护眼层 (Filter Layer) - 放在底层
        filterView = View(this).apply {
            // 使用固定的深琥珀色，透明度由 warmthIntensity 单独控制
            setBackgroundColor(Color.rgb(255, 140, 0)) // Deep Warm Amber
            alpha = 0f
            visibility = View.GONE
        }
        overlayContainer.addView(filterView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // 3. 亮度层 (Dimmer Layer) - 放在顶层
        dimmerView = View(this).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0f
        }
        overlayContainer.addView(dimmerView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    private fun createFloatingBall() {
        floatingBallView = ImageView(this).apply {
            setImageResource(R.drawable.screenlight_app_icon) 
            
            // --- 优化：将悬浮球裁剪为圆形 ---
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        // 缩小裁剪区域
                        val margin = 5 // 可以根据需要调整缩小量
                        outline.setOval(margin, margin, view.width - margin, view.height - margin)
                    }
                }
                clipToOutline = true
            }
            // --- 优化结束 ---
            
            setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f
                
                // Long press logic
                private val handler = Handler(Looper.getMainLooper())
                private var isLongPress = false
                private val longPressRunnable = Runnable {
                    isLongPress = true
                    // Perform haptic feedback
                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    // Open App
                    val intent = Intent(this@ScreenFilterService, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                }

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = floatingBallParams.x
                            initialY = floatingBallParams.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isLongPress = false
                            // Start long press timer
                            handler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - initialTouchX).toInt()
                            val dy = (event.rawY - initialTouchY).toInt()
                            
                            // If moved significantly, cancel long press
                            if (abs(dx) > 10 || abs(dy) > 10) {
                                handler.removeCallbacks(longPressRunnable)
                            }

                            floatingBallParams.x = initialX + dx
                            floatingBallParams.y = initialY + dy
                            
                            try {
                                windowManager.updateViewLayout(floatingBallView, floatingBallParams)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            handler.removeCallbacks(longPressRunnable)
                            if (!isLongPress) {
                                val dx = (event.rawX - initialTouchX).toInt()
                                val dy = (event.rawY - initialTouchY).toInt()
                                if (abs(dx) < 10 && abs(dy) < 10) {
                                     showControlPanel()
                                }
                            }
                            return true
                        }
                    }
                    return false
                }
            })
        }
    }

    private fun createControlPanel() {
        val rootLayout = FrameLayout(this).apply {
             setOnClickListener {
                 removeControlPanel()
             }
        }

        val linearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            
            val backgroundDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#E6212121")) // Dark grey color
                cornerRadius = 100f // Fully rounded corners
            }
            background = backgroundDrawable
            
            setPadding(40, 30, 40, 30) 
            isClickable = true
            layoutParams = FrameLayout.LayoutParams(
                 FrameLayout.LayoutParams.MATCH_PARENT, 
                 FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP
                topMargin = 150 
                leftMargin = 50 
                rightMargin = 50
            }
        }

        val iconLow = ImageView(this).apply {
            setImageResource(android.R.drawable.btn_star_big_off) 
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(60, 60).apply {
                gravity = Gravity.CENTER_VERTICAL
                rightMargin = 20
            }
        }

        val iconHigh = ImageView(this).apply {
            setImageResource(android.R.drawable.btn_star_big_on) 
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(60, 60).apply {
                gravity = Gravity.CENTER_VERTICAL
                leftMargin = 20
            }
        }

        val seekBar = SeekBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            max = 100
            
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val brightness = progress / 100f
                    val curvedBrightness = brightness * brightness 
                    val newAlpha = maxAlpha - (curvedBrightness * (maxAlpha - minAlpha))
                    
                    currentAlpha = newAlpha
                    updateViews()
                    
                    hideControlPanelHandler.removeCallbacks(hideControlPanelRunnable)
                    hideControlPanelHandler.postDelayed(hideControlPanelRunnable, 3000)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                     hideControlPanelHandler.removeCallbacks(hideControlPanelRunnable)
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    hideControlPanelHandler.postDelayed(hideControlPanelRunnable, 3000)
                    serviceScope.launch {
                        dataStore.edit {
                            it[AppSettings.ALPHA_KEY] = currentAlpha
                        }
                    }
                }
            })
        }
        
        linearLayout.addView(iconLow)
        linearLayout.addView(seekBar)
        linearLayout.addView(iconHigh)
        rootLayout.addView(linearLayout)
        
        controlPanelView = rootLayout

        controlPanelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT, 
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, 
            PixelFormat.TRANSLUCENT
        )
    }

    private fun showControlPanel() {
        if (isControlPanelAdded) {
            hideControlPanelHandler.removeCallbacks(hideControlPanelRunnable)
            hideControlPanelHandler.postDelayed(hideControlPanelRunnable, 3000)
            return
        }

        val rootLayout = controlPanelView as? FrameLayout
        val linearLayout = rootLayout?.getChildAt(0) as? LinearLayout
        val seekBar = linearLayout?.getChildAt(1) as? SeekBar 
        
        if (seekBar != null) {
             val linearProgress = (maxAlpha - currentAlpha) / (maxAlpha - minAlpha)
             val displayBrightness = kotlin.math.sqrt(linearProgress.coerceAtLeast(0f))
             seekBar.progress = (displayBrightness * 100).toInt()
        }

        try {
            windowManager.addView(controlPanelView, controlPanelParams)
            isControlPanelAdded = true
            hideControlPanelHandler.postDelayed(hideControlPanelRunnable, 3000)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeControlPanel() {
        if (isControlPanelAdded) {
            try {
                windowManager.removeView(controlPanelView)
                isControlPanelAdded = false
                hideControlPanelHandler.removeCallbacks(hideControlPanelRunnable)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        
        if (intent?.action == ACTION_TOGGLE) {
             if (isOverlayAdded) {
                 stopSelf()
                 return START_NOT_STICKY
             }
        }

        serviceScope.launch {
            // 1. 从 DataStore 加载保存的设置
            val prefs = dataStore.data.first()
            var savedAlpha = prefs[AppSettings.ALPHA_KEY] ?: 0.3f
            var savedIsBlueLightEnabled = prefs[AppSettings.IS_BLUE_LIGHT_KEY] ?: false
            var savedWarmth = prefs[AppSettings.WARMTH_KEY] ?: 0.5f

            // 2. 如果 Intent 中有新值，则覆盖保存值
            if (intent?.hasExtra(EXTRA_ALPHA) == true) {
                savedAlpha = intent.getFloatExtra(EXTRA_ALPHA, savedAlpha)
            }
            if (intent?.hasExtra(EXTRA_BLUE_LIGHT) == true) {
                savedIsBlueLightEnabled = intent.getBooleanExtra(EXTRA_BLUE_LIGHT, savedIsBlueLightEnabled)
            }
            if (intent?.hasExtra(EXTRA_WARMTH) == true) {
                savedWarmth = intent.getFloatExtra(EXTRA_WARMTH, savedWarmth)
            }

            // 3. 更新 Service 的当前状态
            currentAlpha = savedAlpha
            isBlueLightFilterEnabled = savedIsBlueLightEnabled
            warmthIntensity = savedWarmth

            // 4. 应用最终设置到视图
            if (!isOverlayAdded) {
                addOverlayView()
                addFloatingBall() 
                startForegroundService()
            } else {
                updateViews()
                // 确保更新视图后再添加悬浮球
                if (!isFloatingBallAdded) {
                    addFloatingBall()
                }
                // 再次确保前台服务运行
                startForegroundService()
            }
        }

        return START_NOT_STICKY
    }
    
    // 分离后的视图更新逻辑
    private fun updateViews() {
        // 1. 更新亮度层
        dimmerView.alpha = currentAlpha
        
        // 2. 更新护眼层
        if (isBlueLightFilterEnabled) {
            filterView.visibility = View.VISIBLE
            // 护眼强度独立计算，范围映射到 0.0 ~ 0.5 (避免过深)
            // 强度只由 warmthIntensity 决定，不受 currentAlpha 影响
            filterView.alpha = warmthIntensity * 0.5f 
        } else {
            filterView.visibility = View.GONE
        }
    }

    private fun addOverlayView() {
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        // 初始化视图状态
        updateViews()
        
        try {
            windowManager.addView(overlayContainer, layoutParams)
            isOverlayAdded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun addFloatingBall() {
        if (isFloatingBallAdded) return

        floatingBallParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, 
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100 
            width = 150 
            height = 150
        }

        try {
            windowManager.addView(floatingBallView, floatingBallParams)
            isFloatingBallAdded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = Intent(this, ScreenFilterService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_filter_running))
            .setContentText(getString(R.string.notification_tap_to_close))
            .setSmallIcon(R.drawable.screenlight_app_icon)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_close_filter),
                stopPendingIntent
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        try {
            unregisterReceiver(notificationReceiver)
        } catch (e: IllegalArgumentException) {
        }
        
        removeControlPanel()
        
        if (isOverlayAdded) {
            try {
                // 移除的是容器
                windowManager.removeView(overlayContainer)
                isOverlayAdded = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        if (isFloatingBallAdded) {
            try {
                windowManager.removeView(floatingBallView)
                isFloatingBallAdded = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }
}