package com.reevajs.reeva.core

import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.utils.expect
import java.util.*
import java.util.concurrent.LinkedBlockingDeque

abstract class Task(val realm: Realm) {
    abstract fun execute()
}

abstract class Microtask {
    abstract fun execute()
}

class RunnableTask(realm: Realm, private val runnable: Runnable) : Task(realm) {
    override fun execute() = runnable.run()
}

class RunnableMicrotask(private val runnable: Runnable) : Microtask() {
    override fun execute() = runnable.run()
}

/**
 * Facilitates long-running JS applications which require complex execution flow. Emulates
 * the NodeJS event loop.
 *
 * This event loop accepts and processes tasks and microtasks. At the core, it revolves
 * around the idea of a tick. A tick is one event loop pass. Every pass, the event loop will
 * process at most one task. If there are no tasks, it will block until it receives one.
 *
 * After processing a task, any microtasks that are queued will be executed until the microtask
 * queue is empty. So at the end of a tick, the task queue has one less task (assuming none
 * were queued while executing a task), and the microtask queue is empty.
 *
 * This event loop also provides a mechanism for scheduling tasks to run some time in the
 * future via scheduleTask.
 */
class EventLoop {
    private val initializationThread = Thread.currentThread()
    private val taskQueue = LinkedBlockingDeque<Task>()
    private val microtaskQueue = LinkedBlockingDeque<Microtask>()
    private val scheduledTasks = TreeSet<DelayedTask> { a, b -> a.targetTime.compareTo(b.targetTime) }
    private val sleepLock = Object()

    /**
     * Perform an event loop tick.
     */
    fun tick() {
        expect(Thread.currentThread() == initializationThread)

        while (taskQueue.isEmpty() && scheduledTasks.isEmpty()) {
            synchronized(sleepLock) {
                sleepLock.wait()
            }
        }

        synchronized(scheduledTasks) {
            val currentTime = System.currentTimeMillis()
            val scheduledTask = scheduledTasks.first()

            if (scheduledTask != null && scheduledTask.targetTime <= currentTime) {
                scheduledTasks.pollFirst()
                scheduledTask.task.execute()

                while (microtaskQueue.isNotEmpty())
                    microtaskQueue.removeFirst().execute()

                return
            } else if (scheduledTask != null && taskQueue.isEmpty()) {
                // If we only have scheduled tasks, then we can just sleep here, but with a timeout
                // according to the delay of the first scheduled task. We use the same sleep lock so
                // that if we get a task while we're sleeping, we'll wake up
                synchronized(sleepLock) {
                    sleepLock.wait(scheduledTask.targetTime - currentTime)
                }
                return
            }
        }

        val task = taskQueue.pollFirst() ?: return

        synchronized(task.realm) {
            task.execute()

            while (microtaskQueue.isNotEmpty())
                microtaskQueue.removeFirst().execute()
        }
    }

    /**
     * Schedules a task to be run at the beginning of the next tick. If there is a scheduled
     * task which can be run the next tick, then the scheduled task takes priority.
     *
     * TODO: Validate that the above behavior mirrors node.
     */
    fun submitTask(task: Task) {
        taskQueue.addLast(task)
        synchronized(sleepLock) {
            sleepLock.notify()
        }
    }

    /**
     * Submits a microtask to be ran at the end of the currently executing task.
     */
    fun submitMicrotask(microtask: Microtask) {
        expect(Thread.currentThread() == initializationThread)
        microtaskQueue.addLast(microtask)
    }

    /**
     * Schedules a task to be run some time in the future. This method is very lightweight;
     * it does not use Timers or Threads.
     */
    fun scheduleTask(task: Task, delayMillis: Long) {
        expect(delayMillis >= 0)

        val targetTime = System.currentTimeMillis() + delayMillis
        synchronized(scheduledTasks) {
            scheduledTasks.add(DelayedTask(targetTime, task))
        }
    }

    /**
     * Blocks the thread until all tasks and microtasks have been executed.
     */
    fun blockUntilTasksEmpty() {
        while (taskQueue.isNotEmpty() || microtaskQueue.isNotEmpty())
            tick()
    }

    /**
     * Blocks the thread until all tasks, microtasks, and scheduled tasks have
     * been executed.
     */
    fun blockUntilEmpty() {
        while (taskQueue.isNotEmpty() || microtaskQueue.isNotEmpty() || scheduledTasks.isNotEmpty())
            tick()
    }

    data class DelayedTask(val targetTime: Long = System.currentTimeMillis(), val task: Task)
}
