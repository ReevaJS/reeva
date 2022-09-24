package com.reevajs.reeva.runtime.wrappers

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSBigInt
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.toIntegerOrInfinity
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue

class JSBigIntProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        val realm = Agent.activeAgent.getActiveRealm()
        defineOwnProperty("constructor", realm.bigIntCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "BigInt".toValue(), Descriptor.CONFIGURABLE or Descriptor.HAS_BASIC)
        defineBuiltin("toString", 0, ::toString)
        defineBuiltin("valueOf", 0, ::valueOf)
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSBigIntProto(realm).initialize()

        private fun thisBigIntValue(thisValue: JSValue, methodName: String): JSBigInt {
            if (thisValue is JSBigInt)
                return thisValue
            if (thisValue !is JSObject)
                Errors.IncompatibleMethodCall("BigInt.prototype.$methodName").throwTypeError()
            return thisValue.getSlotOrNull(SlotName.BigIntData)
                ?: Errors.IncompatibleMethodCall("BigInt.prototype.$methodName").throwTypeError()
        }

        @ECMAImpl("21.2.3.3")
        @JvmStatic
        fun toString(arguments: JSArguments): JSValue {
            val bigInt = thisBigIntValue(arguments.thisValue, "toString")
            val radixArg = arguments.argument(0)
            val radix = if (radixArg != JSUndefined) {
                arguments.argument(0).toIntegerOrInfinity().asInt
            } else 10
            if (radix < 2 || radix > 36)
                Errors.Number.InvalidRadix(radix).throwRangeError()
            return bigInt.number.toString(radix).toValue()
        }

        @ECMAImpl("21.2.3.3")
        @JvmStatic
        fun valueOf(arguments: JSArguments): JSValue {
            return thisBigIntValue(arguments.thisValue, "valueOf")
        }
    }
}
