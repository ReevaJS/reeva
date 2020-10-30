package me.mattco.reeva.core

import me.mattco.reeva.Reeva
import me.mattco.reeva.core.tasks.Microtask
import me.mattco.reeva.core.tasks.Task
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.errors.JSErrorObject
import me.mattco.reeva.utils.expect
import java.util.concurrent.ConcurrentLinkedDeque

class Agent private constructor() {
    @Volatile
    internal var shouldLoop = false

    private val tasks = ConcurrentLinkedDeque<Task<*>>()
    private val microTasks = ConcurrentLinkedDeque<Microtask>()
    private val runningContextStack = ConcurrentLinkedDeque<ExecutionContext>()

    val runningContext: ExecutionContext
        get() = runningContextStack.last

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

        val runningContext: ExecutionContext
            get() = activeAgent.runningContext


        @JvmStatic
        fun pushContext(context: ExecutionContext) {
            activeAgent.runningContextStack.add(context)
        }

        @JvmStatic
        fun popContext() {
            activeAgent.runningContextStack.removeLast()
        }

        @JvmStatic
        inline fun ifError(block: (JSValue) -> Unit) {
            if (hasError())
                block(runningContext.error!!)
        }

        @JvmStatic
        fun hasError() = runningContext.error != null

        @JvmStatic
        fun throwError(error: JSValue) {
            expect(runningContext.error == null)
            runningContext.error = error
        }

        @JvmStatic
        inline fun <reified T : JSErrorObject> throwError(message: String? = null) {
            ifError { return }
            val obj = T::class.java.getDeclaredMethod("create", Realm::class.java, String::class.java)
                .invoke(null, runningContext.realm, message) as T
            throwError(obj)
        }
    }
}
