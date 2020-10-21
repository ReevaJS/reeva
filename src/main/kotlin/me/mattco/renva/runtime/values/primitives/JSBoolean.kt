package me.mattco.renva.runtime.values.primitives

import me.mattco.renva.runtime.values.JSValue

sealed class JSBoolean(val value: Boolean) : JSValue()

object JSTrue : JSBoolean(true)

object JSFalse : JSBoolean(false)
