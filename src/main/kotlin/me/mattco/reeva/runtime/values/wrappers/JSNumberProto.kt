package me.mattco.reeva.runtime.values.wrappers

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.values.objects.Descriptor
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.primitives.JSNumber
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.shouldThrowError
import me.mattco.reeva.utils.throwError

class JSNumberProto private constructor(realm: Realm) : JSNumberObject(realm, JSNumber.ZERO) {
    override fun init() {
        // No super call to avoid prototype complications

        internalSetPrototype(realm.objectProto)
        defineOwnProperty("prototype", realm.objectProto, 0)
        configureInstanceProperties()

        defineOwnProperty("constructor", realm.numberCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    @ECMAImpl("20.1.3.2")
    @JSMethod("toExponential", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun toExponential(thisValue: JSValue, arguments: JSArguments): JSValue {
        TODO()
    }

    @ECMAImpl("20.1.3.3")
    @JSMethod("toFixed", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun toFixed(thisValue: JSValue, arguments: JSArguments): JSValue {
        TODO()
    }

    @ECMAImpl("20.1.3.3")
    @JSMethod("toLocaleString", 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun toLocaleString(thisValue: JSValue, arguments: JSArguments): JSValue {
        TODO()
    }

    @ECMAImpl("20.1.3.3")
    @JSMethod("toPrecision", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun toPrecision(thisValue: JSValue, arguments: JSArguments): JSValue {
        TODO()
    }

    @ECMAImpl("20.1.3.3")
    @JSMethod("toString", 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun toString(thisValue: JSValue, arguments: JSArguments): JSValue {
        TODO()
    }

    @JSThrows
    @ECMAImpl("20.1.3.3")
    @JSMethod("valueOf", 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun valueOf(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisNumberValue(thisValue)
    }

    companion object {
        fun create(realm: Realm) = JSNumberProto(realm).also { it.init() }

        @JSThrows
        @ECMAImpl("20.1.3")
        private fun thisNumberValue(value: JSValue): JSNumber {
            if (value.isNumber)
                return value as JSNumber
            if (value is JSNumberObject)
                return value.number
            throwError<JSTypeErrorObject>("Number method called on incompatible object ${Operations.toPrintableString(value)}")
            return JSNumber.ZERO
        }
    }
}
