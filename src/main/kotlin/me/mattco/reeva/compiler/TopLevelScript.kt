package me.mattco.reeva.compiler

import me.mattco.reeva.runtime.contexts.ExecutionContext

abstract class TopLevelScript {
    abstract fun run(context: ExecutionContext)
}
