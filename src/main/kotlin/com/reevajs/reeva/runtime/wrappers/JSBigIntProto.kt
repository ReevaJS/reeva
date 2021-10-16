package com.reevajs.reeva.runtime.wrappers

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSBigInt
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue

class JSBigIntProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("constructor", realm.bigIntCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineOwnProperty(Realm.`@@toStringTag`, "BigInt".toValue(), Descriptor.CONFIGURABLE or Descriptor.HAS_BASIC)
        defineBuiltin("toString", 0, ReevaBuiltin.BigIntProtoToString)
        defineBuiltin("valueOf", 0, ReevaBuiltin.BigIntProtoValueOf)
    }

    companion object {
        fun create(realm: Realm) = JSBigIntProto(realm).initialize()

        private fun thisBigIntValue(realm: Realm, thisValue: JSValue, methodName: String): JSBigInt {
            if (thisValue is JSBigInt)
                return thisValue
            if (thisValue !is JSObject)
                Errors.IncompatibleMethodCall("BigInt.prototype.$methodName").throwTypeError(realm)
            return thisValue.getSlotAs(SlotName.BigIntData)
                ?: Errors.IncompatibleMethodCall("BigInt.prototype.$methodName").throwTypeError(realm)
        }

        @ECMAImpl("21.2.3.3")
        @JvmStatic
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

        @ECMAImpl("21.2.3.3")
        @JvmStatic
        fun valueOf(realm: Realm, arguments: JSArguments): JSValue {
            return thisBigIntValue(realm, arguments.thisValue, "valueOf")
        }
    }
}
