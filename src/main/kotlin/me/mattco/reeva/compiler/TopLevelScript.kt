package me.mattco.reeva.compiler

import me.mattco.reeva.runtime.JSValue

abstract class TopLevelScript {
    abstract fun run(): JSValue
}
