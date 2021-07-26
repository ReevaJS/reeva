package com.reevajs.reeva.runtime.primitives

import com.reevajs.reeva.runtime.JSArguments
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.functions.JSFunction

class JSAccessor(
    var getter: JSFunction?,
    var setter: JSFunction?,
) : JSValue() {
    fun callGetter(thisValue: JSValue) =
        getter?.call(JSArguments(emptyList(), thisValue)) ?: JSUndefined

    fun callSetter(thisValue: JSValue, value: JSValue) =
        setter?.call(JSArguments(listOf(value), thisValue)) ?: JSUndefined
}
