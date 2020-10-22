package me.mattco.reeva.runtime.values.wrappers

import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.objects.Attributes
import me.mattco.reeva.runtime.values.objects.Descriptor
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.primitives.JSNumber
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.shouldThrowError

class JSNumberProto private constructor(realm: Realm) : JSNumberObject(realm, JSNumber(0)) {
    override fun init() {
        // No super call to avoid prototype complications

        internalSetPrototype(realm.objectProto)
        defineOwnProperty("prototype", Descriptor(realm.objectProto, Attributes(0)))
        configureInstanceProperties()

        defineOwnProperty("constructor", Descriptor(realm.numberCtor, Attributes(Attributes.CONFIGURABLE and Attributes.WRITABLE)))
    }

    @ECMAImpl("Number.prototype.toExponential", "20.1.3.2")
    @JSMethod("toExponential", 1, Attributes.CONFIGURABLE and Attributes.WRITABLE)
    fun toExponential(thisValue: JSValue, arguments: JSArguments): JSValue {
        TODO()
    }

    @ECMAImpl("Number.prototype.toFixed", "20.1.3.3")
    @JSMethod("toFixed", 1, Attributes.CONFIGURABLE and Attributes.WRITABLE)
    fun toFixed(thisValue: JSValue, arguments: JSArguments): JSValue {
        TODO()
    }

    @ECMAImpl("Number.prototype.toLocaleString", "20.1.3.3")
    @JSMethod("toLocaleString", 0, Attributes.CONFIGURABLE and Attributes.WRITABLE)
    fun toLocaleString(thisValue: JSValue, arguments: JSArguments): JSValue {
        TODO()
    }

    @ECMAImpl("Number.prototype.toPrecision", "20.1.3.3")
    @JSMethod("toPrecision", 1, Attributes.CONFIGURABLE and Attributes.WRITABLE)
    fun toPrecision(thisValue: JSValue, arguments: JSArguments): JSValue {
        TODO()
    }

    @ECMAImpl("Number.prototype.toString", "20.1.3.3")
    @JSMethod("toString", 0, Attributes.CONFIGURABLE and Attributes.WRITABLE)
    fun toString(thisValue: JSValue, arguments: JSArguments): JSValue {
        TODO()
    }

    @ECMAImpl("Number.prototype.valueOf", "20.1.3.3")
    @JSMethod("valueOf", 0, Attributes.CONFIGURABLE and Attributes.WRITABLE)
    fun valueOf(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisNumberValue(thisValue)
    }

    companion object {
        fun create(realm: Realm) = JSNumberProto(realm).also { it.init() }

        @ECMAImpl("thisNumberValue", "20.1.3")
        private fun thisNumberValue(value: JSValue): JSNumber {
            if (value.isNumber)
                return value as JSNumber
            if (value is JSNumberObject)
                return value.number
            shouldThrowError("TypeError")
        }
    }
}
