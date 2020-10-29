package me.mattco.reeva.runtime.wrappers

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.JSNativePropertyGetter
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.primitives.JSString
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.toValue

class JSStringProto private constructor(realm: Realm) : JSStringObject(realm, JSString("")) {
    override fun init() {
        // No super call to avoid prototype complications

        internalSetPrototype(realm.objectProto)
        defineOwnProperty("prototype", realm.objectProto, 0)
        configureInstanceProperties()

        defineOwnProperty("constructor", realm.stringCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    @JSNativePropertyGetter("length", attributes = 0)
    fun getLength(thisValue: JSValue): JSValue {
        expect(thisValue is JSStringObject)
        return thisValue.string.string.length.toValue()
    }

    companion object {
        fun create(realm: Realm) = JSStringProto(realm).also { it.init() }
    }
}
