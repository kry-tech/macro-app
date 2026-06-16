package com.example.meuapp

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.content.ContextCompat
import kotlin.math.abs

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

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideBubbleHalf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
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
    }

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

        layoutParams = WindowManager.LayoutParams(
            size,
            size,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Posição inicial: canto inferior direito
            x = context.resources.displayMetrics.widthPixels - size
            y = context.resources.displayMetrics.heightPixels - size
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
                    // Não inicia drag enquanto a bolha está voltando
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
                    layoutParams.x = initialX + deltaX.toInt()
                    layoutParams.y = initialY + deltaY.toInt()
                    windowManager.updateViewLayout(bubbleView, layoutParams)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isDragging) {
                    // Foi um clique
                    openApp()
                }
                resetIdleTimer()
                return true
            }
        }
        return false
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
        val screenHeight = resources.displayMetrics.heightPixels
        val viewWidth = bubbleView.width
        val viewHeight = bubbleView.height

        // Descobre qual borda está mais próxima
        val centerX = layoutParams.x + viewWidth / 2
        val centerY = layoutParams.y + viewHeight / 2

        val distLeft = centerX
        val distRight = screenWidth - centerX
        val distTop = centerY
        val distBottom = screenHeight - centerY

        val targetX: Int
        val targetY: Int

        val minHorizontal = minOf(distLeft, distRight)
        val minVertical = minOf(distTop, distBottom)

        if (minHorizontal < minVertical) {
            // Esconde pela lateral
            if (distLeft < distRight) {
                targetX = -viewWidth / 2
            } else {
                targetX = screenWidth - viewWidth / 2
            }
            targetY = layoutParams.y
        } else {
            // Esconde pela vertical
            if (distTop < distBottom) {
                targetY = -viewHeight / 2
            } else {
                targetY = screenHeight - viewHeight / 2
            }
            targetX = layoutParams.x
        }

        animateBubble(targetX, targetY)
    }

    private fun restoreBubble() {
        if (!isHidden) return
        isHidden = false

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val viewWidth = bubbleView.width
        val viewHeight = bubbleView.height

        var targetX = layoutParams.x
        var targetY = layoutParams.y

        // Verifica se está escondida (metade fora) e reposiciona para ficar completamente visível
        if (targetX < 0) {
            targetX = 0
        } else if (targetX > screenWidth - viewWidth) {
            targetX = screenWidth - viewWidth
        }
        if (targetY < 0) {
            targetY = 0
        } else if (targetY > screenHeight - viewHeight) {
            targetY = screenHeight - viewHeight
        }

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
                // Após restaurar, ativa timer novamente
                if (!isHidden) resetIdleTimer()
            }
        })

        animX.duration = 300
        animY.duration = 300
        animX.start()
        animY.start()
    }

    // Suporte à animação via setter/getter
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
