package me.mattco.reeva.runtime.values.primitives

import me.mattco.reeva.runtime.values.JSValue

sealed class JSBoolean(val value: Boolean) : JSValue()

object JSTrue : JSBoolean(true)

object JSFalse : JSBoolean(false)
