package me.mattco.reeva.runtime.values.primitives

import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.runtime.annotations.NativeGetterSignature
import me.mattco.reeva.runtime.annotations.NativeSetterSignature
import me.mattco.reeva.runtime.values.JSValue

class JSNativeProperty(
    private val getter: NativeGetterSignature?,
    private val setter: NativeSetterSignature?
) : JSValue() {
    @JSThrows
    fun get(thisValue: JSValue) = getter?.invoke(thisValue) ?: JSUndefined

    @JSThrows
    fun set(thisValue: JSValue, value: JSValue) = setter?.invoke(thisValue, value) ?: JSUndefined
}
