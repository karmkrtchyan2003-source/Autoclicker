package com.karem.autoclicker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var triggerView: View
    private lateinit var targetView: View
    private lateinit var triggerParams: WindowManager.LayoutParams
    private lateinit var targetParams: WindowManager.LayoutParams

    private val handler = Handler(Looper.getMainLooper())
    private var clicking = false
    private var intervalMs = 50
    private var locked = true

    private val clickRunnable = object : Runnable {
        override fun run() {
            if (clicking) {
                val targetX = targetParams.x + (targetView.width / 2f)
                val targetY = targetParams.y + (targetView.height / 2f)
                ClickAccessibilityService.instance?.performClick(targetX, targetY)
                handler.postDelayed(this, intervalMs.toLong())
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        intervalMs = prefs.getInt(Prefs.INTERVAL_MS, 50)

        val overlayType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val commonFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

        targetView = LayoutInflater.from(this).inflate(R.layout.overlay_target, null)
        targetParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            commonFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(Prefs.TARGET_X, 300)
            y = prefs.getInt(Prefs.TARGET_Y, 600)
        }
        windowManager.addView(targetView, targetParams)
        setupTargetDrag(prefs)

        triggerView = LayoutInflater.from(this).inflate(R.layout.overlay_trigger, null)
        triggerParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            commonFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(Prefs.TRIGGER_X, 100)
            y = prefs.getInt(Prefs.TRIGGER_Y, 900)
        }
        windowManager.addView(triggerView, triggerParams)
        setupTrigger(prefs)
    }

    private fun setupTargetDrag(prefs: android.content.SharedPreferences) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f

        targetView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = targetParams.x
                    initialY = targetParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    targetParams.x = initialX + (event.rawX - touchX).toInt()
                    targetParams.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(targetView, targetParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    prefs.edit()
                        .putInt(Prefs.TARGET_X, targetParams.x)
                        .putInt(Prefs.TARGET_Y, targetParams.y)
                        .apply()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupTrigger(prefs: android.content.SharedPreferences) {
        val lockToggle = triggerView.findViewById<TextView>(R.id.lockToggle)

        lockToggle.setOnClickListener {
            locked = !locked
            lockToggle.text = if (locked) "🔒" else "🔓"
        }

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f

        triggerView.setOnTouchListener { _, event ->
            if (locked) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        clicking = true
                        handler.post(clickRunnable)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        clicking = false
                        true
                    }
                    else -> false
                }
            } else {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = triggerParams.x
                        initialY = triggerParams.y
                        touchX = event.rawX
                        touchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        triggerParams.x = initialX + (event.rawX - touchX).toInt()
                        triggerParams.y = initialY + (event.rawY - touchY).toInt()
                        windowManager.updateViewLayout(triggerView, triggerParams)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        prefs.edit()
                            .putInt(Prefs.TRIGGER_X, triggerParams.x)
                            .putInt(Prefs.TRIGGER_Y, triggerParams.y)
                            .apply()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun startForegroundWithNotification() {
        val channelId = "autoclicker_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "АвтоКликер", NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java)
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(this, 0, openIntent, pendingFlags)

        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("АвтоКликер активен")
            .setContentText("Нажми, чтобы открыть настройки")
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(1, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clicking = false
        handler.removeCallbacks(clickRunnable)
        if (::windowManager.isInitialized) {
            if (::triggerView.isInitialized) windowManager.removeView(triggerView)
            if (::targetView.isInitialized) windowManager.removeView(targetView)
        }
    }
}
