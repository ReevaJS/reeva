package me.mattco.reeva.runtime.wrappers

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSBigInt
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.argument
import me.mattco.reeva.utils.toValue

class JSBigIntProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("constructor", realm.bigIntCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineOwnProperty(Realm.`@@toStringTag`, "BigInt".toValue(), Descriptor.CONFIGURABLE or Descriptor.HAS_BASIC)
        defineNativeFunction("toString", 0, ::toString)
        defineNativeFunction("valueOf", 0, ::valueOf)
    }

    fun toString(thisValue: JSValue, arguments: JSArguments): JSValue {
        val bigInt = thisBigIntValue(thisValue, "toString")
        val radixArg = arguments.argument(0)
        val radix = if (radixArg != JSUndefined) {
            Operations.toIntegerOrInfinity(arguments.argument(0)).asInt
        } else 10
        if (radix < 2 || radix > 36)
            Errors.Number.InvalidRadix(radix).throwRangeError()
        return bigInt.number.toString(radix).toValue()
    }

    fun valueOf(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisBigIntValue(thisValue, "valueOf")
    }

    companion object {
        private fun thisBigIntValue(thisValue: JSValue, methodName: String): JSBigInt {
            if (thisValue is JSBigInt)
                return thisValue
            if (thisValue is JSBigIntObject)
                return thisValue.value
            Errors.IncompatibleMethodCall("BigInt.prototype.$methodName").throwTypeError()
        }

        fun create(realm: Realm) = JSBigIntProto(realm).initialize()
    }
}
