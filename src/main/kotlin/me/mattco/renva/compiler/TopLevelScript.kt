package me.mattco.renva.compiler

import me.mattco.renva.runtime.contexts.ExecutionContext

abstract class TopLevelScript {
    abstract fun run(context: ExecutionContext)
}
