package me.mattco.reeva.runtime.primitives

import me.mattco.reeva.runtime.JSValue

sealed class JSBoolean(val boolean: Boolean) : JSValue() {
    fun inv() = if (this is JSTrue) JSFalse else JSTrue
}

object JSTrue : JSBoolean(true)

object JSFalse : JSBoolean(false)
