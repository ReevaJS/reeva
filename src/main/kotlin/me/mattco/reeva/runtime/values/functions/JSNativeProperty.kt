package me.mattco.reeva.runtime.values.functions

import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.runtime.annotations.NativeGetterSignature
import me.mattco.reeva.runtime.annotations.NativeSetterSignature
import me.mattco.reeva.runtime.values.JSValue

class JSNativeProperty(
    private val getter: NativeGetterSignature?,
    private val setter: NativeSetterSignature?
) : JSValue() {
    val hasGetter = getter != null
    val hasSetter = setter != null

    @JSThrows
    fun callGetter(thisValue: JSValue) = getter!!.invoke(thisValue)

    @JSThrows
    fun callSetter(thisValue: JSValue, value: JSValue) = setter!!.invoke(thisValue, value)
}
