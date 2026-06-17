package com.example.meuapp

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText

class StepEditorActivity : AppCompatActivity() {

    private lateinit var triggerId: String
    private var trigger: MacroManager.Trigger? = null
    private lateinit var stepAdapter: StepAdapter

    companion object {
        var waitingForCoordinate: ((Int, Int) -> Unit)? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_step_editor)

        triggerId = intent.getStringExtra("trigger_id") ?: run {
            finish()
            return
        }
        trigger = MacroManager.getTriggers().find { it.id == triggerId }
        if (trigger == null) {
            finish()
            return
        }

        findViewById<TextInputEditText>(R.id.edit_trigger_name).apply {
            setText(trigger!!.name)
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) trigger!!.name = text.toString()
            }
        }

        val recycler = findViewById<RecyclerView>(R.id.recycler_steps)
        stepAdapter = StepAdapter(trigger!!.steps.toMutableList()) { pos ->
            val step = trigger!!.steps[pos]
            if (step.type == MacroManager.StepType.CLICK) {
                // Inicia o picker via FloatingService
                val intent = Intent(this, FloatingService::class.java).apply {
                    action = "START_PICKER"
                    putExtra("step_index", pos)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                Toast.makeText(this, "Arraste o alvo vermelho e toque em CONFIRMAR", Toast.LENGTH_LONG).show()
                waitingForCoordinate = { x, y ->
                    val updated = trigger!!.steps.toMutableList()
                    updated[pos] = updated[pos].copy(x = x, y = y)
                    trigger!!.steps.clear()
                    trigger!!.steps.addAll(updated)
                    stepAdapter.notifyItemChanged(pos)
                    waitingForCoordinate = null
                }
            }
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = stepAdapter

        findViewById<View>(R.id.button_add_click).setOnClickListener {
            val linearLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
            }
            val editX = EditText(this).apply {
                hint = "Coordenada X"
                inputType = InputType.TYPE_CLASS_NUMBER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val editY = EditText(this).apply {
                hint = "Coordenada Y"
                inputType = InputType.TYPE_CLASS_NUMBER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            linearLayout.addView(editX)
            linearLayout.addView(editY)

            AlertDialog.Builder(this)
                .setTitle("Coordenadas do clique")
                .setView(linearLayout)
                .setPositiveButton("OK") { _, _ ->
                    val x = editX.text.toString().toIntOrNull() ?: 0
                    val y = editY.text.toString().toIntOrNull() ?: 0
                    trigger!!.steps.add(MacroManager.Step(MacroManager.StepType.CLICK, x, y))
                    stepAdapter.notifyItemInserted(trigger!!.steps.size - 1)
                }
                .setNeutralButton("Escolher na tela") { _, _ ->
                    val intent = Intent(this, FloatingService::class.java).apply {
                        action = "START_PICKER"
                        putExtra("add_new", true)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    waitingForCoordinate = { x, y ->
                        trigger!!.steps.add(MacroManager.Step(MacroManager.StepType.CLICK, x, y))
                        stepAdapter.notifyItemInserted(trigger!!.steps.size - 1)
                        waitingForCoordinate = null
                    }
                    Toast.makeText(this, "Posicione o alvo vermelho e confirme", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        findViewById<View>(R.id.button_add_delay).setOnClickListener {
            val editDelay = EditText(this).apply {
                hint = "Milissegundos"
                inputType = InputType.TYPE_CLASS_NUMBER
                setText("500")
            }
            AlertDialog.Builder(this)
                .setTitle("Tempo de espera (ms)")
                .setView(editDelay)
                .setPositiveButton("OK") { _, _ ->
                    val delay = editDelay.text.toString().toLongOrNull() ?: 500L
                    trigger!!.steps.add(MacroManager.Step(MacroManager.StepType.DELAY, delayMs = delay))
                    stepAdapter.notifyItemInserted(trigger!!.steps.size - 1)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        findViewById<View>(R.id.button_save).setOnClickListener {
            trigger!!.name = findViewById<TextInputEditText>(R.id.edit_trigger_name).text.toString()
            MacroManager.updateTrigger(trigger!!)
            Toast.makeText(this, "Gatilho salvo", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        waitingForCoordinate = null
    }
}

class StepAdapter(
    private val steps: MutableList<MacroManager.Step>,
    private val onPickCoord: (Int) -> Unit
) : RecyclerView.Adapter<StepAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val descText: TextView = view.findViewById(R.id.text_step_desc)
        val pickButton: View = view.findViewById(R.id.button_pick_coord)
        val deleteButton: View = view.findViewById(R.id.button_delete_step)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_step, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val step = steps[position]
        holder.descText.text = when (step.type) {
            MacroManager.StepType.CLICK -> "Clique (${step.x}, ${step.y})"
            MacroManager.StepType.DELAY -> "Espera ${step.delayMs} ms"
        }
        holder.pickButton.visibility = if (step.type == MacroManager.StepType.CLICK) View.VISIBLE else View.GONE
        holder.pickButton.setOnClickListener { onPickCoord(position) }
        holder.deleteButton.setOnClickListener {
            steps.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    override fun getItemCount() = steps.size
}
