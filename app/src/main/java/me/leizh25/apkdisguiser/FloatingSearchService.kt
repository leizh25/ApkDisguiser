package me.leizh25.apkdisguiser

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageButton
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class FloatingSearchActivity : MainActivity() {
    override val floatingSearch = true
}

class FloatingSearchService : Service() {
    companion object {
        var isShowing by androidx.compose.runtime.mutableStateOf(false)
            private set
    }

    private var bubble: ImageButton? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this) || intent?.action == "stop") {
            stopSelf()
            return START_NOT_STICKY
        }
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel("floating_search", "悬浮搜索", NotificationManager.IMPORTANCE_LOW))
        }
        val stop = PendingIntent.getService(this, 1, Intent(this, javaClass).setAction("stop"),
            PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, "floating_search")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle("悬浮搜索已开启")
            .setOngoing(true)
            .addAction(0, "关闭", stop)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
        if (bubble == null) {
            try {
                showBubble()
            } catch (_: SecurityException) {
                stopSelf()
            } catch (_: WindowManager.BadTokenException) {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION", "ClickableViewAccessibility")
    private fun showBubble() {
        val windowManager = getSystemService(WindowManager::class.java)
        val size = (48 * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(size, size,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
        params.gravity = Gravity.TOP or Gravity.LEFT
        params.x = resources.displayMetrics.widthPixels - size
        params.y = resources.displayMetrics.heightPixels / 3
        val button = ImageButton(this)
        button.contentDescription = "打开悬浮搜索"
        button.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            colors = intArrayOf(0xff39327e.toInt(), 0xff161a39.toInt())
            orientation = android.graphics.drawable.GradientDrawable.Orientation.TL_BR
            setStroke((resources.displayMetrics.density).toInt().coerceAtLeast(1), 0xff776cbe.toInt())
        }
        button.setImageResource(R.drawable.ic_launcher_foreground)
        button.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        button.setPadding(0, 0, 0, 0)
        button.elevation = 3 * resources.displayMetrics.density
        button.setOnClickListener {
            startActivity(Intent(this, FloatingSearchActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var dragging = false
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    dragging = dragging || abs(dx) > slop || abs(dy) > slop
                    if (dragging) {
                        val metrics = resources.displayMetrics
                        params.x = (initialX + dx.toInt()).coerceIn(0, (metrics.widthPixels - size).coerceAtLeast(0))
                        params.y = (initialY + dy.toInt()).coerceIn(0, (metrics.heightPixels - size).coerceAtLeast(0))
                        windowManager.updateViewLayout(view, params)
                    }
                }
                MotionEvent.ACTION_UP -> if (!dragging) view.performClick()
            }
            true
        }
        windowManager.addView(button, params)
        bubble = button
        isShowing = true
    }

    override fun onDestroy() {
        bubble?.let { getSystemService(WindowManager::class.java).removeView(it) }
        bubble = null
        isShowing = false
        super.onDestroy()
    }
}
