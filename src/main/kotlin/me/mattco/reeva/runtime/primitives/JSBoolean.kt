package me.mattco.reeva.runtime.primitives

import me.mattco.reeva.runtime.JSValue

sealed class JSBoolean(val boolean: Boolean) : JSValue()

object JSTrue : JSBoolean(true)

object JSFalse : JSBoolean(false)
