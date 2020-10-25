package me.mattco.reeva.runtime.values.primitives

import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.functions.JSFunction

class JSAccessor(
    private val getter: JSFunction?,
    private val setter: JSFunction?,
) : JSValue() {
    @JSThrows
    fun callGetter(thisValue: JSValue) = getter?.call(thisValue, emptyList()) ?: JSUndefined

    @JSThrows
    fun callSetter(thisValue: JSValue, value: JSValue) = setter?.call(thisValue, listOf(value)) ?: JSUndefined
}
