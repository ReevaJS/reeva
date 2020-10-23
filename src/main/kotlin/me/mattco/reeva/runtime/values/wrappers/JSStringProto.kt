package me.mattco.reeva.runtime.values.wrappers

import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.JSNativePropertyGetter
import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.values.objects.Attributes
import me.mattco.reeva.runtime.values.objects.Descriptor
import me.mattco.reeva.runtime.values.primitives.JSString
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.shouldThrowError
import me.mattco.reeva.utils.throwError
import me.mattco.reeva.utils.toValue

class JSStringProto private constructor(realm: Realm) : JSStringObject(realm, JSString("")) {
    override fun init() {
        // No super call to avoid prototype complications

        internalSetPrototype(realm.objectProto)
        defineOwnProperty("prototype", Descriptor(realm.objectProto, Attributes(0)))
        configureInstanceProperties()

        defineOwnProperty("constructor", Descriptor(realm.stringCtor, Attributes(Attributes.CONFIGURABLE and Attributes.WRITABLE)))
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
