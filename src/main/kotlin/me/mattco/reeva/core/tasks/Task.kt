package me.mattco.reeva.core.tasks

import me.mattco.reeva.core.ExecutionContext

abstract class Task<T> {
    abstract fun makeContext(): ExecutionContext

    abstract fun execute(): T
}
