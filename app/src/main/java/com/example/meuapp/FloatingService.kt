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

    // Bolha principal
    private var mainContainer: FrameLayout? = null
    private var bubbleView: ImageView? = null
    private var menuView: LinearLayout? = null

    // Botões de gatilho flutuantes
    private val triggerViews = mutableMapOf<String, Button>()

    // Seletor de coordenadas
    private var pickerContainer: FrameLayout? = null
    private var pickerTarget: ImageView? = null
    private var pickerConfirmButton: Button? = null

    // Estado de arrasto
    private var dragStartX = 0
    private var dragStartY = 0
    private var dragStartTouchX = 0f
    private var dragStartTouchY = 0f
    private var dragging = false

    // Tamanhos
    private val bubbleSize by lazy { 60.dpToPx() }
    private val menuWidth by lazy { 220.dpToPx() }
    private val menuHeight by lazy { 80.dpToPx() }
    private val margin by lazy { 8.dpToPx() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        criarBolhaPrincipal()
        atualizarBotoesGatilho()
        MacroManager.listeners.add { atualizarBotoesGatilho() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            if (action == "START_PICKER") {
                abrirPicker()
            }
        }
        return START_STICKY
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

            val btnEditor = Button(context).apply {
                text = "Editor"
                setOnClickListener {
                    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                    }
                    menuView?.visibility = View.GONE
                }
            }
            val btnFechar = Button(context).apply {
                text = "Fechar"
                setOnClickListener {
                    stopSelf()
                }
            }
            addView(btnEditor)
            addView(btnFechar)
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

    // ---------- BOTÕES DE GATILHO ----------
    private fun atualizarBotoesGatilho() {
        val triggers = MacroManager.getTriggers()
        val idsAtuais = triggers.map { it.id }.toSet()
        val paraRemover = triggerViews.keys.filter { it !in idsAtuais }
        paraRemover.forEach { id ->
            triggerViews[id]?.let { windowManager.removeView(it) }
            triggerViews.remove(id)
        }
        for (trigger in triggers) {
            if (!triggerViews.containsKey(trigger.id)) {
                criarBotaoGatilho(trigger)
            } else {
                triggerViews[trigger.id]?.text = trigger.name
            }
        }
    }

    private fun criarBotaoGatilho(trigger: MacroManager.Trigger) {
        val button = Button(this).apply {
            text = trigger.name
            setBackgroundColor(0xFF03A9F4.toInt())
            setOnTouchListener { view, event ->
                handleMacroTouch(view, event) {
                    executarMacro(trigger)
                }
            }
        }
        addOverlayView(button, 300, 100 * (triggerViews.size + 1), 200, 100)
        triggerViews[trigger.id] = button
    }

    private fun executarMacro(trigger: MacroManager.Trigger) {
        val service = MacroAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, "Serviço de acessibilidade não ativo", Toast.LENGTH_SHORT).show()
            return
        }
        var delayAcc = 0L
        for (step in trigger.steps) {
            Handler(Looper.getMainLooper()).postDelayed({
                when (step.type) {
                    MacroManager.StepType.CLICK -> service.performClick(step.x, step.y)
                    MacroManager.StepType.DELAY -> {}
                }
            }, delayAcc)
            if (step.type == MacroManager.StepType.DELAY) {
                delayAcc += step.delayMs
            }
        }
        Toast.makeText(this, "Macro '${trigger.name}' iniciada", Toast.LENGTH_SHORT).show()
    }

    // ---------- SELETOR DE COORDENADAS ----------
    private fun abrirPicker() {
        if (pickerContainer != null) return

        pickerContainer = FrameLayout(this).apply {
            setBackgroundColor(0x44000000)
        }

        val targetSize = 100.dpToPx()
        pickerTarget = ImageView(this).apply {
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFFF0000.toInt())
            }
            setImageDrawable(drawable)
        }

        val targetParams = WindowManager.LayoutParams(
            targetSize,
            targetSize,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 300
            y = 300
        }

        pickerConfirmButton = Button(this).apply {
            text = "CONFIRMAR"
            setBackgroundColor(0xFF4CAF50.toInt())
            setOnClickListener {
                val x = targetParams.x + pickerTarget!!.width / 2
                val y = targetParams.y + pickerTarget!!.height / 2
                StepEditorActivity.waitingForCoordinate?.invoke(x, y)
                fecharPicker()
                Toast.makeText(this@FloatingService, "Posição capturada ($x, $y)", Toast.LENGTH_SHORT).show()
            }
        }

        val confirmParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 50
        }

        // Adiciona diretamente ao WindowManager
        windowManager.addView(pickerTarget, targetParams)
        windowManager.addView(pickerConfirmButton, confirmParams)

        // Arrasto do alvo
        pickerTarget.setOnTouchListener { _, event -> handlePickerTargetTouch(event, targetParams) }
    }

    private fun handlePickerTargetTouch(event: MotionEvent, params: WindowManager.LayoutParams): Boolean {
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
                    windowManager.updateViewLayout(pickerTarget, params)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                return true
            }
        }
        return false
    }

    private fun fecharPicker() {
        pickerTarget?.let { windowManager.removeView(it) }
        pickerConfirmButton?.let { windowManager.removeView(it) }
        pickerTarget = null
        pickerConfirmButton = null
        pickerContainer = null
    }

    // ---------- UTILITÁRIOS DE ARRASTO ----------
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
                            mainContainer?.layoutParams == params -> mainContainer
                            else -> triggerViews.values.find { it.layoutParams == params }
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
        triggerViews.values.forEach { windowManager.removeView(it) }
        fecharPicker()
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
