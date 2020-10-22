package me.mattco.reeva.runtime.values.functions

import me.mattco.reeva.runtime.annotations.NativeGetterSignature
import me.mattco.reeva.runtime.annotations.NativeSetterSignature
import me.mattco.reeva.runtime.values.JSValue

class JSNativeProperty(
    private val getter: NativeGetterSignature?,
    private val setter: NativeSetterSignature?
) : JSValue() {
    val hasGetter = getter != null
    val hasSetter = setter != null

    fun callGetter() = getter!!.invoke()
    fun callSetter(value: JSValue) = setter!!.invoke(value)
}
