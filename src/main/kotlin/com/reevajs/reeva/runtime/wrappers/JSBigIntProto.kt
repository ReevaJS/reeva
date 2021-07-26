package com.reevajs.reeva.runtime.wrappers

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSArguments
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.SlotName
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSBigInt
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue

class JSBigIntProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("constructor", realm.bigIntCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineOwnProperty(Realm.`@@toStringTag`, "BigInt".toValue(), Descriptor.CONFIGURABLE or Descriptor.HAS_BASIC)
        defineNativeFunction("toString", 0, ::toString)
        defineNativeFunction("valueOf", 0, ::valueOf)
    }

    fun toString(realm: Realm, arguments: JSArguments): JSValue {
        val bigInt = thisBigIntValue(realm, arguments.thisValue, "toString")
        val radixArg = arguments.argument(0)
        val radix = if (radixArg != JSUndefined) {
            Operations.toIntegerOrInfinity(realm, arguments.argument(0)).asInt
        } else 10
        if (radix < 2 || radix > 36)
            Errors.Number.InvalidRadix(radix).throwRangeError(realm)
        return bigInt.number.toString(radix).toValue()
    }

    fun valueOf(realm: Realm, arguments: JSArguments): JSValue {
        return thisBigIntValue(realm, arguments.thisValue, "valueOf")
    }

    companion object {
        private fun thisBigIntValue(realm: Realm, thisValue: JSValue, methodName: String): JSBigInt {
            if (thisValue is JSBigInt)
                return thisValue
            if (thisValue !is JSObject)
                Errors.IncompatibleMethodCall("BigInt.prototype.$methodName").throwTypeError(realm)
            return thisValue.getSlotAs(SlotName.BigIntData) ?:
                Errors.IncompatibleMethodCall("BigInt.prototype.$methodName").throwTypeError(realm)
        }

        fun create(realm: Realm) = JSBigIntProto(realm).initialize()
    }
}
