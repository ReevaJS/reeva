package me.mattco.reeva.runtime.values.wrappers

import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.objects.Attributes
import me.mattco.reeva.runtime.values.objects.Descriptor
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.primitives.JSBoolean
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.shouldThrowError
import me.mattco.reeva.utils.toValue

// TODO: This apparently has to extend from JSBooleanObject somehow
class JSBooleanProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        defineOwnProperty("constructor", Descriptor(realm.booleanCtor, Attributes(Attributes.CONFIGURABLE and Attributes.WRITABLE)))
    }

    @ECMAImpl("Boolean.prototype.toString", "19.3.3.2")
    @JSMethod("toString", 0, Attributes.CONFIGURABLE and Attributes.WRITABLE)
    fun toString_(thisValue: JSValue, arguments: JSArguments): JSValue {
        val b = thisBooleanValue(thisValue)
        return if (b.value) "true".toValue() else "false".toValue()
    }

    @ECMAImpl("Boolean.prototype.valueOf", "19.3.3.2")
    @JSMethod("valueOf", 0, Attributes.CONFIGURABLE and Attributes.WRITABLE)
    fun valueOf(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisBooleanValue(thisValue)
    }

    companion object {
        fun create(realm: Realm) = JSBooleanProto(realm).also { it.init() }

        private fun thisBooleanValue(value: JSValue): JSBoolean {
            if (value.isBoolean)
                return value as JSBoolean
            if (value is JSBooleanObject)
                return value.value
            shouldThrowError("TypeError")
        }
    }
}
