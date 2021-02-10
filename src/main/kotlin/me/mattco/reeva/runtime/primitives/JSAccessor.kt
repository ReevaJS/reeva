package me.mattco.reeva.runtime.primitives

import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.functions.JSFunction

class JSAccessor(
    var getter: JSFunction?,
    var setter: JSFunction?,
) : JSValue() {
    fun callGetter(thisValue: JSValue) =
        getter?.call(JSArguments(emptyList(), thisValue)) ?: JSUndefined

    fun callSetter(thisValue: JSValue, value: JSValue) =
        setter?.call(JSArguments(listOf(value), thisValue)) ?: JSUndefined
}
