package com.example.meuapp

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import kotlin.math.abs

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private var containerView: FrameLayout? = null
    private var bubbleView: ImageView? = null
    private var menuView: LinearLayout? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val bubbleSize = 60.dpToPx()
        val menuWidth = 200.dpToPx()
        val menuHeight = 150.dpToPx()

        // Container principal
        containerView = FrameLayout(this)

        // Ícone (bolha)
        bubbleView = ImageView(this).apply {
            val circle = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF4CAF50.toInt())
            }
            setImageDrawable(circle)
            layoutParams = FrameLayout.LayoutParams(bubbleSize, bubbleSize).apply {
                gravity = Gravity.CENTER
            }
            setOnClickListener {
                // Ao clicar na bolha, abre/fecha o menu
                menuView?.let { menu ->
                    menu.visibility = if (menu.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                }
            }
        }

        // Menu expansível
        menuView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(16, 16, 16, 16)
            visibility = View.GONE

            layoutParams = FrameLayout.LayoutParams(menuWidth, menuHeight).apply {
                gravity = Gravity.CENTER
            }

            val title = Button(context).apply {
                text = "MENU"
                isEnabled = false
            }
            val close = Button(context).apply {
                text = "Fechar"
                setOnClickListener {
                    visibility = View.GONE
                }
            }
            val action = Button(context).apply {
                text = "Abrir App"
                setOnClickListener {
                    // Abre a MainActivity
                    val intent = packageManager.getLaunchIntentForPackage(packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    }
                    visibility = View.GONE
                }
            }
            addView(title)
            addView(action)
            addView(close)
        }

        containerView?.addView(bubbleView)
        containerView?.addView(menuView)

        // Configuração da janela flutuante
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            bubbleSize + menuWidth,  // largura suficiente para conter ambos
            bubbleSize + menuHeight, // altura suficiente
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        // Touch para arrastar (no container inteiro)
        containerView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    if (!isDragging && (abs(deltaX) > 10 || abs(deltaY) > 10)) {
                        // Iniciou arrasto: esconde o menu
                        menuView?.visibility = View.GONE
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = initialX + deltaX.toInt()
                        params.y = initialY + deltaY.toInt()
                        windowManager.updateViewLayout(containerView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Se não foi arrasto e a bolha não foi clicada (o clique já tratou o menu), não faz nada extra
                    true
                }
                else -> false
            }
        }

        windowManager.addView(containerView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        containerView?.let {
            windowManager.removeView(it)
        }
        containerView = null
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
