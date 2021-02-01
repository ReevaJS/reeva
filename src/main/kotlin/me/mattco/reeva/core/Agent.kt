package me.mattco.reeva.core

import me.mattco.reeva.core.tasks.Microtask
import me.mattco.reeva.core.tasks.Task
import me.mattco.reeva.utils.expect
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArrayList

class Agent private constructor() {
    @Volatile
    internal var shouldLoop = false

    private val tasks = ConcurrentLinkedDeque<Task<*>>()
    private val microTasks = ConcurrentLinkedDeque<Microtask>()
    val runningContextStack = CopyOnWriteArrayList<ExecutionContext>()

    val byteOrder = ByteOrder.nativeOrder()
    val isLittleEndian: Boolean
        get() = byteOrder == ByteOrder.LITTLE_ENDIAN
    val isBigEndian: Boolean
        get() = byteOrder == ByteOrder.BIG_ENDIAN

    init {
        activeAgentList.add(this)
    }

    internal inline fun <reified T> runTask(task: Task<T>): T {
        tasks.add(task)
        val result = runTasks()
        expect(result is T)
        return result
    }

    internal fun submitTask(task: Task<*>) {
        tasks.add(task)
    }

    internal fun submitMicrotask(microTask: Microtask) {
        microTasks.add(microTask)
    }

    private fun runTasks(): Any? {
        expect(tasks.isNotEmpty())

        val result = runTaskLoop()
        while (tasks.isNotEmpty() || shouldLoop) {
            if (tasks.isNotEmpty())
                runTaskLoop()
        }

        return result
    }

    private fun runTaskLoop(): Any? {
        val task = tasks.removeFirst()
        val context = task.makeContext()
        runningContextStack.add(context)
        val result = task.execute()

        while (microTasks.isNotEmpty())
            microTasks.removeFirst().execute()

        runningContextStack.remove(context)
        return result
    }

    internal fun teardown() {
        shouldLoop = false
        tasks.clear()
        microTasks.clear()
    }

    companion object {
        internal val activeAgentList = mutableListOf<Agent>()

        private val activeAgents = object : ThreadLocal<Agent>() {
            override fun initialValue() = Agent()
        }

        val activeAgent: Agent
            get() = activeAgents.get()

        @JvmStatic
        val runningContext: ExecutionContext
            get() = activeAgent.runningContextStack.last()


        @JvmStatic
        fun pushContext(context: ExecutionContext) {
            activeAgent.runningContextStack.add(context)
        }

        @JvmStatic
        fun popContext() {
            activeAgent.runningContextStack.removeLast()
        }
    }
}
