package me.mattco.jsthing.runtime.values.primitives

import me.mattco.jsthing.runtime.values.JSValue

sealed class JSBoolean(val value: Boolean) : JSValue()

object JSTrue : JSBoolean(true)

object JSFalse : JSBoolean(false)
