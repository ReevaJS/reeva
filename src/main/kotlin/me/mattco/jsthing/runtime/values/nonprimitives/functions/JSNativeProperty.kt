package me.mattco.jsthing.runtime.values.nonprimitives.functions

import me.mattco.jsthing.runtime.contexts.ExecutionContext
import me.mattco.jsthing.runtime.values.JSValue

class JSNativeProperty(
    private val getter: NativeGetterSignature?,
    private val setter: NativeSetterSignature?
) : JSValue() {
    val hasGetter = getter != null
    val hasSetter = setter != null

    fun callGetter(context: ExecutionContext) = getter!!.invoke(context)
    fun callSetter(context: ExecutionContext, value: JSValue) = setter!!.invoke(context, value)
}
