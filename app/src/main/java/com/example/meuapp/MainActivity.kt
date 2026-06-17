package com.example.meuapp

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : ComponentActivity() {

    private lateinit var triggerAdapter: TriggerAdapter

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startFloatingService()
            } else {
                Toast.makeText(this, "Permissão de sobreposição é necessária para os botões flutuantes.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        triggerAdapter = TriggerAdapter(
            onEdit = { trigger -> openStepEditor(trigger.id) },
            onDelete = { trigger -> MacroManager.removeTrigger(trigger.id) }
        )

        findViewById<RecyclerView>(R.id.recycler_triggers).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = triggerAdapter
        }

        findViewById<FloatingActionButton>(R.id.fab_add_trigger).setOnClickListener {
            val newTrigger = MacroManager.Trigger()
            MacroManager.addTrigger(newTrigger)
            openStepEditor(newTrigger.id)
        }

        // Solicita sobreposição ao iniciar (apenas uma vez)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            startFloatingService()
        }
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun openStepEditor(triggerId: String) {
        val intent = Intent(this, StepEditorActivity::class.java).apply {
            putExtra("trigger_id", triggerId)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        triggerAdapter.submitList(MacroManager.getTriggers())
    }
}

class TriggerAdapter(
    private val onEdit: (MacroManager.Trigger) -> Unit,
    private val onDelete: (MacroManager.Trigger) -> Unit
) : RecyclerView.Adapter<TriggerAdapter.ViewHolder>() {

    private var items = listOf<MacroManager.Trigger>()

    fun submitList(list: List<MacroManager.Trigger>) {
        items = list
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.text_trigger_name)
        val editButton: Button = view.findViewById(R.id.button_edit_steps)
        val deleteButton: Button = view.findViewById(R.id.button_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trigger, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val trigger = items[position]
        holder.nameText.text = trigger.name
        holder.editButton.setOnClickListener { onEdit(trigger) }
        holder.deleteButton.setOnClickListener { onDelete(trigger) }
    }

    override fun getItemCount() = items.size
}
