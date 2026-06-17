package com.example.meuapp

import java.util.UUID

object MacroManager {
    private val triggers = mutableListOf<Trigger>()
    val listeners = mutableListOf<() -> Unit>()

    data class Step(
        val type: StepType,
        val x: Int = 0,
        val y: Int = 0,
        val delayMs: Long = 500
    )

    enum class StepType { CLICK, DELAY }

    data class Trigger(
        val id: String = UUID.randomUUID().toString(),
        var name: String = "Gatilho",
        val steps: MutableList<Step> = mutableListOf()
    )

    fun getTriggers(): List<Trigger> = triggers.toList()

    fun addTrigger(trigger: Trigger) {
        triggers.add(trigger)
        notifyListeners()
    }

    fun removeTrigger(id: String) {
        triggers.removeAll { it.id == id }
        notifyListeners()
    }

    fun updateTrigger(updatedTrigger: Trigger) {
        val index = triggers.indexOfFirst { it.id == updatedTrigger.id }
        if (index != -1) {
            triggers[index] = updatedTrigger
            notifyListeners()
        }
    }

    private fun notifyListeners() {
        listeners.forEach { it.invoke() }
    }
}
