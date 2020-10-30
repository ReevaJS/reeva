package me.mattco.reeva.runtime.wrappers

import me.mattco.reeva.core.Agent.Companion.throwError
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.primitives.JSBoolean
import me.mattco.reeva.runtime.primitives.JSFalse
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.toValue

class JSBooleanProto private constructor(realm: Realm) : JSBooleanObject(realm, JSFalse) {
    override fun init() {
        // No super call to avoid prototype complications

        internalSetPrototype(realm.objectProto)
        defineOwnProperty("prototype", realm.objectProto, 0)
        configureInstanceProperties()

        defineOwnProperty("constructor", realm.booleanCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    @JSThrows
    @ECMAImpl("19.3.3.2")
    @JSMethod("toString", 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun toString(thisValue: JSValue, arguments: JSArguments): JSValue {
        val b = thisBooleanValue(thisValue)
        return if (b.value) "true".toValue() else "false".toValue()
    }

    @JSThrows
    @ECMAImpl("19.3.3.2")
    @JSMethod("valueOf", 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
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
