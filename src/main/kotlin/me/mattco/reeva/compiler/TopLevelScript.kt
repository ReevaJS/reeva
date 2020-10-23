package me.mattco.reeva.compiler

import me.mattco.reeva.runtime.contexts.ExecutionContext
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.errors.JSErrorObject

abstract class TopLevelScript {
    abstract fun run(context: ExecutionContext): JSValue
}
