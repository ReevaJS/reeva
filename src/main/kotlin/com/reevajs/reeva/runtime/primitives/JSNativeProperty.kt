package com.reevajs.reeva.runtime.primitives

import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.utils.NativeGetterSignature
import com.reevajs.reeva.utils.NativeSetterSignature

class JSNativeProperty(
    private val getter: NativeGetterSignature?,
    private val setter: NativeSetterSignature?
) : JSValue() {
    fun get(thisValue: JSValue) = getter?.invoke(thisValue) ?: JSUndefined

    fun set(thisValue: JSValue, value: JSValue) = setter?.invoke(thisValue, value) ?: JSUndefined
}
