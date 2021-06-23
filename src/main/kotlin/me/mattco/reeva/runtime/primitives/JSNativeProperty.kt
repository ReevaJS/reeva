package me.mattco.reeva.runtime.primitives

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.utils.NativeGetterSignature
import me.mattco.reeva.utils.NativeSetterSignature

class JSNativeProperty(
    private val getter: NativeGetterSignature?,
    private val setter: NativeSetterSignature?
) : JSValue() {
    fun get(realm: Realm, thisValue: JSValue) = getter?.invoke(realm, thisValue) ?: JSUndefined

    fun set(realm: Realm, thisValue: JSValue, value: JSValue) = setter?.invoke(realm, thisValue, value) ?: JSUndefined
}
