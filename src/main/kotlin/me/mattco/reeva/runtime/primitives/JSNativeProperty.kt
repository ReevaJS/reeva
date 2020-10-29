package me.mattco.reeva.runtime.primitives

import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.utils.NativeGetterSignature
import me.mattco.reeva.utils.NativeSetterSignature

class JSNativeProperty(
    private val getter: NativeGetterSignature?,
    private val setter: NativeSetterSignature?
) : JSValue() {
    @JSThrows
    fun get(thisValue: JSValue) = getter?.invoke(thisValue) ?: JSUndefined

    @JSThrows
    fun set(thisValue: JSValue, value: JSValue) = setter?.invoke(thisValue, value) ?: JSUndefined
}
