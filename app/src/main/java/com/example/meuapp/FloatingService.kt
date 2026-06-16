package com.example.meuapp

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: View
    private lateinit var layoutParams: WindowManager.LayoutParams

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var isHidden = false

    // Margens seguras (em pixels) para evitar barras do sistema
    private var statusBarHeight = 0
    private var navigationBarHeight = 0

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideBubbleHalf() }

    companion object {
        const val CHANNEL_ID = "floating_bubble_channel"
        const val NOTIFICATION_ID = 101
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        calculateSystemBarHeights()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        createBubbleView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (bubbleView.parent == null) {
            windowManager.addView(bubbleView, layoutParams)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bubbleView.parent != null) {
            windowManager.removeView(bubbleView)
        }
        hideHandler.removeCallbacks(hideRunnable)
        stopForeground(true)
    }

    private fun calculateSystemBarHeights() {
        val resourceIdStatus = resources.getIdentifier("status_bar_height", "dimen", "android")
        statusBarHeight = if (resourceIdStatus > 0) resources.getDimensionPixelSize(resourceIdStatus) else 0

        val resourceIdNavigation = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        navigationBarHeight = if (resourceIdNavigation > 0) resources.getDimensionPixelSize(resourceIdNavigation) else 0
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bolha flutuante",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Canal para manter o serviço da bolha ativo"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Bolha ativa")
        .setContentText("Toque para abrir o app")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .build()

    private fun createBubbleView() {
        val size = 60.dpToPx()
        val circle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFF4CAF50.toInt())
        }
        bubbleView = ImageView(this).apply {
            setImageDrawable(circle)
            layoutParams = WindowManager.LayoutParams(size, size)
            setOnTouchListener { _, event -> handleTouch(event) }
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            size,
            size,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Posição inicial segura (canto inferior direito, acima da barra de navegação)
            val displayWidth = this@FloatingService.resources.displayMetrics.widthPixels
            val displayHeight = this@FloatingService.resources.displayMetrics.heightPixels
            x = displayWidth - size
            y = max(statusBarHeight, displayHeight - navigationBarHeight - size)
        }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                hideHandler.removeCallbacks(hideRunnable)
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                initialX = layoutParams.x
                initialY = layoutParams.y
                isDragging = false

                if (isHidden) {
                    restoreBubble()
                    return true
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - initialTouchX
                val deltaY = event.rawY - initialTouchY
                if (!isDragging && (abs(deltaX) > 10 || abs(deltaY) > 10)) {
                    isDragging = true
                }
                if (isDragging) {
                    val newX = initialX + deltaX.toInt()
                    val newY = initialY + deltaY.toInt()
                    // Aplica limites seguros
                    layoutParams.x = clampX(newX)
                    layoutParams.y = clampY(newY)
                    windowManager.updateViewLayout(bubbleView, layoutParams)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isDragging) {
                    openApp()
                }
                resetIdleTimer()
                return true
            }
        }
        return false
    }

    private fun clampX(value: Int): Int {
        val viewWidth = bubbleView.width
        return value.coerceIn(0, resources.displayMetrics.widthPixels - viewWidth)
    }

    private fun clampY(value: Int): Int {
        val viewHeight = bubbleView.height
        val maxY = resources.displayMetrics.heightPixels - navigationBarHeight - viewHeight
        return value.coerceIn(statusBarHeight, maxY)
    }

    private fun openApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    private fun resetIdleTimer() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 5000) // 5 segundos
    }

    private fun hideBubbleHalf() {
        if (isHidden) return
        isHidden = true

        val screenWidth = resources.displayMetrics.widthPixels
        val viewWidth = bubbleView.width
        val viewHeight = bubbleView.height

        val centerX = layoutParams.x + viewWidth / 2
        val centerY = layoutParams.y + viewHeight / 2

        val distLeft = centerX
        val distRight = screenWidth - centerX
        val distTop = centerY - statusBarHeight
        val distBottom = (resources.displayMetrics.heightPixels - navigationBarHeight) - centerY

        val targetX: Int
        val targetY: Int

        val minHorizontal = minOf(distLeft, distRight)
        val minVertical = minOf(distTop, distBottom)

        if (minHorizontal < minVertical) {
            if (distLeft < distRight) {
                targetX = -viewWidth / 2
            } else {
                targetX = screenWidth - viewWidth / 2
            }
            targetY = clampY(layoutParams.y)
        } else {
            if (distTop < distBottom) {
                targetY = statusBarHeight - viewHeight / 2
            } else {
                targetY = resources.displayMetrics.heightPixels - navigationBarHeight - viewHeight / 2
            }
            targetX = clampX(layoutParams.x)
        }

        animateBubble(targetX, targetY)
    }

    private fun restoreBubble() {
        if (!isHidden) return
        isHidden = false

        var targetX = clampX(layoutParams.x)
        var targetY = clampY(layoutParams.y)

        // Se ainda estiver parcialmente fora, move para a posição totalmente visível mais próxima
        val viewWidth = bubbleView.width
        val viewHeight = bubbleView.height
        if (targetX < 0) targetX = 0
        if (targetX > resources.displayMetrics.widthPixels - viewWidth)
            targetX = resources.displayMetrics.widthPixels - viewWidth
        if (targetY < statusBarHeight) targetY = statusBarHeight
        if (targetY > resources.displayMetrics.heightPixels - navigationBarHeight - viewHeight)
            targetY = resources.displayMetrics.heightPixels - navigationBarHeight - viewHeight

        animateBubble(targetX, targetY)
    }

    private fun animateBubble(targetX: Int, targetY: Int) {
        val animX = ObjectAnimator.ofInt(this, "bubbleX", layoutParams.x, targetX)
        val animY = ObjectAnimator.ofInt(this, "bubbleY", layoutParams.y, targetY)

        animX.addUpdateListener { animation ->
            layoutParams.x = animation.animatedValue as Int
            windowManager.updateViewLayout(bubbleView, layoutParams)
        }
        animY.addUpdateListener { animation ->
            layoutParams.y = animation.animatedValue as Int
            windowManager.updateViewLayout(bubbleView, layoutParams)
        }

        animX.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (!isHidden) resetIdleTimer()
            }
        })

        animX.duration = 300
        animY.duration = 300
        animX.start()
        animY.start()
    }

    @Suppress("unused")
    fun getBubbleX() = layoutParams.x
    @Suppress("unused")
    fun setBubbleX(x: Int) { /* atualizado via update listener */ }

    @Suppress("unused")
    fun getBubbleY() = layoutParams.y
    @Suppress("unused")
    fun setBubbleY(y: Int) { /* atualizado via update listener */ }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
