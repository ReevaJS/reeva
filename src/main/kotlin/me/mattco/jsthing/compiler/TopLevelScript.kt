package me.mattco.jsthing.compiler

import me.mattco.jsthing.runtime.contexts.ExecutionContext

abstract class TopLevelScript {
    abstract fun run(context: ExecutionContext)
}
