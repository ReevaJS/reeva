package com.reevajs.reeva.core

import com.reevajs.reeva.Reeva
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class MicrotaskQueue(private val owningAgent: Agent) {
    private val pendingQueue = LinkedList<() -> Unit>()
    private val activeQueue = LinkedBlockingQueue<() -> Unit>()
    private val queueThreadSleeping = AtomicBoolean(false)

    internal val thread = thread(name = "MicrotaskQueue Thread") {
        Reeva.setAgent(owningAgent)

        while (Reeva.running) {
            if (activeQueue.isEmpty()) {
                queueThreadSleeping.set(true)
                Thread.sleep(MILLIS_TO_WAIT_WHEN_NOT_BUSY)
                continue
            }

            queueThreadSleeping.set(false)
            val task = activeQueue.take()
            task()

            // Make sure we keep processing any additional microtasks that may
            // have been submitted by the task above
            checkpoint()
        }
    }

    fun addMicrotask(task: () -> Unit) {
        pendingQueue.add(task)
    }

    fun checkpoint() {
        activeQueue.addAll(pendingQueue)
        pendingQueue.clear()
    }

    fun blockUntilEmpty() {
        @Suppress("ControlFlowWithEmptyBody")
        while (activeQueue.isNotEmpty() || !queueThreadSleeping.get());
    }

    companion object {
        const val MILLIS_TO_WAIT_WHEN_NOT_BUSY = 10L
    }
}