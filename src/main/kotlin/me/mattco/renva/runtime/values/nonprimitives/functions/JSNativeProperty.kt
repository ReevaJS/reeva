package me.mattco.renva.runtime.values.nonprimitives.functions

import me.mattco.renva.runtime.values.JSValue

class JSNativeProperty(
    private val getter: NativeGetterSignature?,
    private val setter: NativeSetterSignature?
) : JSValue() {
    val hasGetter = getter != null
    val hasSetter = setter != null

    fun callGetter() = getter!!.invoke()
    fun callSetter(value: JSValue) = setter!!.invoke(value)
}
