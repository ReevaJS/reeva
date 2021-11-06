package com.reevajs.reeva.core

import java.util.concurrent.LinkedBlockingDeque

class MicrotaskQueue {
    private val pendingMicrotasks = LinkedBlockingDeque<() -> Unit>()

    fun addMicrotask(task: () -> Unit) {
        pendingMicrotasks.addLast(task)
    }

    fun checkpoint() {
        while (!pendingMicrotasks.isEmpty())
            pendingMicrotasks.removeFirst()()
    }
}