package com.example.meuapp

import android.app.Service
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
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import kotlin.math.abs

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager

    // Bolha principal + menu
    private var mainContainer: FrameLayout? = null
    private var bubbleView: ImageView? = null
    private var menuView: LinearLayout? = null
    private var menuButton: Button? = null

    // Botões do macro (disparo e clique)
    private var disparoView: Button? = null
    private var clickView: Button? = null
    private var macroAtivo = false

    // Posições iniciais
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    // Tamanhos
    private val bubbleSize by lazy { 60.dpToPx() }
    private val menuWidth by lazy { 200.dpToPx() }
    private val menuHeight by lazy { 60.dpToPx() }  // apenas um botão, altura reduzida
    private val margin by lazy { 8.dpToPx() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        criarBolhaPrincipal()
    }

    // ---------- BOLHA PRINCIPAL E MENU ----------
    private fun criarBolhaPrincipal() {
        mainContainer = FrameLayout(this)

        // Ícone da bolha
        bubbleView = ImageView(this).apply {
            val circle = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF4CAF50.toInt())
            }
            setImageDrawable(circle)
            layoutParams = FrameLayout.LayoutParams(bubbleSize, bubbleSize).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            setOnClickListener {
                // Alterna visibilidade do menu
                menuView?.let { menu ->
                    menu.visibility = if (menu.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                }
            }
            setOnTouchListener { _, event -> handleBubbleTouch(event) }
        }

        // Menu com apenas um botão "MACRO" (ou "REMOVER MACRO")
        menuView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(16, 16, 16, 16)
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(menuWidth, menuHeight).apply {
                gravity = Gravity.TOP or Gravity.START
                topMargin = bubbleSize + margin
            }

            menuButton = Button(context).apply {
                text = "MACRO"
                setOnClickListener {
                    if (macroAtivo) {
                        removerBotoesMacro()
                    } else {
                        adicionarBotoesMacro()
                    }
                    menuView?.visibility = View.GONE
                }
            }
            addView(menuButton!!)
        }

        mainContainer?.addView(bubbleView)
        mainContainer?.addView(menuView)

        val containerWidth = maxOf(bubbleSize, menuWidth)
        val containerHeight = bubbleSize + menuHeight + margin

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            containerWidth,
            containerHeight,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        windowManager.addView(mainContainer, params)
    }

    private fun handleBubbleTouch(event: MotionEvent): Boolean {
        val params = mainContainer?.layoutParams as? WindowManager.LayoutParams ?: return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - initialTouchX
                val deltaY = event.rawY - initialTouchY
                if (!isDragging && (abs(deltaX) > 10 || abs(deltaY) > 10)) {
                    menuView?.visibility = View.GONE
                    isDragging = true
                }
                if (isDragging) {
                    params.x = initialX + deltaX.toInt()
                    params.y = initialY + deltaY.toInt()
                    windowManager.updateViewLayout(mainContainer, params)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                return true
            }
        }
        return false
    }

    // ---------- BOTÕES DO MACRO ----------
    private fun adicionarBotoesMacro() {
        if (macroAtivo) return
        macroAtivo = true
        menuButton?.text = "REMOVER MACRO"

        // Botão DISPARO (toggle)
        disparoView = Button(this).apply {
            text = "DISPARO (OFF)"
            setBackgroundColor(0xFFFF0000.toInt())  // vermelho = desligado
            setOnClickListener {
                val ativo = clickView?.isEnabled ?: false
                if (ativo) {
                    // Desativa
                    clickView?.isEnabled = false
                    clickView?.setBackgroundColor(0xFF888888.toInt())
                    text = "DISPARO (OFF)"
                    setBackgroundColor(0xFFFF0000.toInt())
                } else {
                    // Ativa
                    clickView?.isEnabled = true
                    clickView?.setBackgroundColor(0xFF4CAF50.toInt()) // verde = ativo
                    text = "DISPARO (ON)"
                    setBackgroundColor(0xFF00FF00.toInt())
                    Toast.makeText(this@FloatingService, "Clique habilitado! Arraste o botão CLIQUE e toque nele para simular.", Toast.LENGTH_SHORT).show()
                }
            }
            // Arrastável
            setOnTouchListener { _, event -> handleDragDisparo(event) }
        }
        addOverlayView(disparoView!!, 300, 300, 150, 100)

        // Botão CLIQUE (alvo)
        clickView = Button(this).apply {
            text = "CLIQUE"
            isEnabled = false
            setBackgroundColor(0xFF888888.toInt()) // cinza = desabilitado
            setOnClickListener {
                if (isEnabled) {
                    // Simular clique (apenas visual)
                    Toast.makeText(this@FloatingService, "Clique simulado na posição do botão!", Toast.LENGTH_SHORT).show()
                    // Aqui entraria a lógica real (ex.: acessibilidade)
                }
            }
            // Arrastável
            setOnTouchListener { _, event -> handleDragClick(event) }
        }
        addOverlayView(clickView!!, 500, 500, 150, 100)
    }

    private fun removerBotoesMacro() {
        if (!macroAtivo) return
        macroAtivo = false
        menuButton?.text = "MACRO"

        disparoView?.let {
            windowManager.removeView(it)
            disparoView = null
        }
        clickView?.let {
            windowManager.removeView(it)
            clickView = null
        }
    }

    private fun addOverlayView(view: View, x: Int, y: Int, width: Int, height: Int) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            width,
            height,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
        windowManager.addView(view, params)
    }

    // Arrasto para o botão DISPARO
    private fun handleDragDisparo(event: MotionEvent): Boolean {
        val view = disparoView ?: return false
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return false
        return handleDrag(event, params)
    }

    // Arrasto para o botão CLIQUE
    private fun handleDragClick(event: MotionEvent): Boolean {
        val view = clickView ?: return false
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return false
        return handleDrag(event, params)
    }

    private fun handleDrag(event: MotionEvent, params: WindowManager.LayoutParams): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - initialTouchX
                val deltaY = event.rawY - initialTouchY
                if (abs(deltaX) > 5 || abs(deltaY) > 5) {
                    params.x = initialX + deltaX.toInt()
                    params.y = initialY + deltaY.toInt()
                    windowManager.updateViewLayout(
                        if (params == disparoView?.layoutParams) disparoView else clickView,
                        params
                    )
                }
                return true
            }
            MotionEvent.ACTION_UP -> return true
        }
        return false
    }

    // ---------- LIMPEZA ----------
    override fun onDestroy() {
        super.onDestroy()
        mainContainer?.let { windowManager.removeView(it) }
        removerBotoesMacro()
        mainContainer = null
        bubbleView = null
        menuView = null
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
