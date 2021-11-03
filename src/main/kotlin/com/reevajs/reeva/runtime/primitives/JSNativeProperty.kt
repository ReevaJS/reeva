package com.reevajs.reeva.runtime.primitives

import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.utils.NativeGetterSignature
import com.reevajs.reeva.utils.NativeSetterSignature

class JSNativeProperty(
    private val getter: NativeGetterSignature?,
    private val setter: NativeSetterSignature?
) : JSValue() {
    fun get(realm: Realm, thisValue: JSValue) = getter?.invoke(realm, thisValue) ?: JSUndefined

    fun set(realm: Realm, thisValue: JSValue, value: JSValue) = setter?.invoke(realm, thisValue, value) ?: JSUndefined
}
