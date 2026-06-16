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
import android.widget.Toast
import kotlin.math.abs

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager

    // Bolha principal + menu
    private var mainContainer: FrameLayout? = null
    private var bubbleView: ImageView? = null
    private var menuView: LinearLayout? = null
    private var menuButton: Button? = null

    // Botões do macro
    private var disparoView: Button? = null
    private var clickView: Button? = null
    private var macroAtivo = false

    // Estado do arrasto
    private var dragStartX = 0
    private var dragStartY = 0
    private var dragStartTouchX = 0f
    private var dragStartTouchY = 0f
    private var dragging = false

    // Tamanhos
    private val bubbleSize by lazy { 60.dpToPx() }
    private val menuWidth by lazy { 200.dpToPx() }
    private val menuHeight by lazy { 60.dpToPx() }
    private val margin by lazy { 8.dpToPx() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        criarBolhaPrincipal()
    }

    // ---------- BOLHA PRINCIPAL ----------
    private fun criarBolhaPrincipal() {
        mainContainer = FrameLayout(this)

        bubbleView = ImageView(this).apply {
            val circle = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF4CAF50.toInt())
            }
            setImageDrawable(circle)
            layoutParams = FrameLayout.LayoutParams(bubbleSize, bubbleSize).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            setOnTouchListener { _, event -> handleBubbleTouch(event) }
        }

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
                    if (macroAtivo) removerBotoesMacro() else adicionarBotoesMacro()
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
        return handleTouch(event, params) {
            menuView?.let {
                it.visibility = if (it.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
    }

    // ---------- BOTÕES MACRO ----------
    private fun adicionarBotoesMacro() {
        if (macroAtivo) return
        macroAtivo = true
        menuButton?.text = "REMOVER MACRO"

        // DISPARO: ao tocar, aciona o clique via acessibilidade
        disparoView = Button(this).apply {
            text = "DISPARO"
            setBackgroundColor(0xFFFF0000.toInt())
            setOnTouchListener { view, event ->
                handleMacroTouch(view, event) { dispararCliqueNaPosicaoAlvo() }
            }
        }
        addOverlayView(disparoView!!, 300, 300, 200, 100)

        // CLIQUE: alvo arrastável
        clickView = Button(this).apply {
            text = "CLIQUE"
            setBackgroundColor(0xFF888888.toInt())
            setOnTouchListener { view, event ->
                handleMacroTouch(view, event) { /* nada */ }
            }
        }
        addOverlayView(clickView!!, 500, 500, 200, 100)
    }

    private fun removerBotoesMacro() {
        if (!macroAtivo) return
        macroAtivo = false
        menuButton?.text = "MACRO"
        disparoView?.let { windowManager.removeView(it); disparoView = null }
        clickView?.let { windowManager.removeView(it); clickView = null }
    }

    private fun handleMacroTouch(view: View, event: MotionEvent, onClick: () -> Unit): Boolean {
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return false
        return handleTouch(event, params, onClick)
    }

    private fun handleTouch(event: MotionEvent, params: WindowManager.LayoutParams, onClick: () -> Unit): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = params.x
                dragStartY = params.y
                dragStartTouchX = event.rawX
                dragStartTouchY = event.rawY
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - dragStartTouchX
                val deltaY = event.rawY - dragStartTouchY
                if (!dragging && (abs(deltaX) > 10 || abs(deltaY) > 10)) {
                    dragging = true
                }
                if (dragging) {
                    params.x = dragStartX + deltaX.toInt()
                    params.y = dragStartY + deltaY.toInt()
                    windowManager.updateViewLayout(
                        when {
                            params == mainContainer?.layoutParams -> mainContainer
                            params == disparoView?.layoutParams -> disparoView
                            else -> clickView
                        },
                        params
                    )
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) {
                    onClick()
                }
                return true
            }
        }
        return false
    }

    private fun dispararCliqueNaPosicaoAlvo() {
        val alvo = clickView ?: run {
            Toast.makeText(this, "Alvo não disponível", Toast.LENGTH_SHORT).show()
            return
        }
        val params = alvo.layoutParams as? WindowManager.LayoutParams ?: return
        val x = params.x + alvo.width / 2
        val y = params.y + alvo.height / 2

        val service = MacroAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, "Serviço de acessibilidade não ativo. Ative em Configurações > Acessibilidade > MacroApp.", Toast.LENGTH_LONG).show()
            return
        }
        service.performClick(x, y)
        Toast.makeText(this, "Clique executado em ($x, $y)", Toast.LENGTH_SHORT).show()
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

    override fun onDestroy() {
        super.onDestroy()
        mainContainer?.let { windowManager.removeView(it) }
        removerBotoesMacro()
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
