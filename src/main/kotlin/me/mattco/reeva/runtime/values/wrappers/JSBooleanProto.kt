package me.mattco.reeva.runtime.values.wrappers

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.values.objects.Attributes
import me.mattco.reeva.runtime.values.objects.Descriptor
import me.mattco.reeva.runtime.values.primitives.JSBoolean
import me.mattco.reeva.runtime.values.primitives.JSFalse
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.shouldThrowError
import me.mattco.reeva.utils.throwError
import me.mattco.reeva.utils.toValue

class JSBooleanProto private constructor(realm: Realm) : JSBooleanObject(realm, JSFalse) {
    override fun init() {
        // No super call to avoid prototype complications

        internalSetPrototype(realm.objectProto)
        defineOwnProperty("prototype", Descriptor(realm.objectProto, Attributes(0)))
        configureInstanceProperties()

        defineOwnProperty("constructor", Descriptor(realm.booleanCtor, Attributes(Attributes.CONFIGURABLE and Attributes.WRITABLE)))
    }

    @JSThrows
    @ECMAImpl("Boolean.prototype.toString", "19.3.3.2")
    @JSMethod("toString", 0, Attributes.CONFIGURABLE and Attributes.WRITABLE)
    fun toString_(thisValue: JSValue, arguments: JSArguments): JSValue {
        val b = thisBooleanValue(thisValue)
        return if (b.value) "true".toValue() else "false".toValue()
    }

    @JSThrows
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
            throwError<JSTypeErrorObject>("Boolean method called on incompatible object ${Operations.toPrintableString(value)}")
            return JSFalse
        }
    }
}
