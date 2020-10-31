package me.mattco.reeva.core.tasks

import me.mattco.reeva.runtime.JSValue

abstract class Microtask {
    abstract fun execute(): JSValue
}
