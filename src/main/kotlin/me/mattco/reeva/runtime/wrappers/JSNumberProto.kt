package me.mattco.reeva.runtime.wrappers

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.primitives.JSNumber
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.throwRangeError
import me.mattco.reeva.utils.throwTypeError
import me.mattco.reeva.utils.toValue

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
        // TODO: Spec-compliant conversion
        val x = thisNumberValue(thisValue)
        val radix = if (arguments.isEmpty()) {
            10
        } else Operations.toIntegerOrInfinity(arguments[0]).asInt

        if (radix < 2 || radix > 36)
            throwRangeError("invalid radix: $radix")

        return if (x.isInt) {
            x.asInt.toString(radix).toValue()
        } else {
            if (radix != 10)
                TODO("Double -> String conversion with radix != 10")
            x.asDouble.toString().toValue()
        }
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
            throwTypeError("Number method called on incompatible object ${Operations.toPrintableString(value)}")
        }
    }
}
