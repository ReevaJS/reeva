package me.mattco.reeva.compiler

import me.mattco.reeva.core.ExecutionContext
import me.mattco.reeva.runtime.JSValue

abstract class TopLevelScript {
    abstract fun run(context: ExecutionContext): JSValue
}
