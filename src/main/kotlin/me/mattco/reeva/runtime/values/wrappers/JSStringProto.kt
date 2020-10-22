package me.mattco.reeva.runtime.values.wrappers

import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.JSNativePropertyGetter
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.utils.shouldThrowError
import me.mattco.reeva.utils.toValue

class JSStringProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    @JSNativePropertyGetter("length", attributes = 0)
    fun getLength(thisValue: JSValue): JSValue {
        if (thisValue !is JSStringObject)
            shouldThrowError("TypeError")
        return thisValue.string.string.length.toValue()
    }

    companion object {
        fun create(realm: Realm) = JSStringProto(realm).also { it.init() }
    }
}
