package com.reevajs.reeva.runtime.wrappers

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.*
import com.reevajs.reeva.runtime.toNumeric
import com.reevajs.reeva.utils.toValue
import kotlin.math.abs

class JSNumberCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Number", 1) {
    override fun init() {
        super.init()

        defineOwnProperty("EPSILON", Math.ulp(1.0).toValue(), 0)
        defineOwnProperty("MAX_SAFE_INTEGER", AOs.MAX_SAFE_INTEGER.toValue(), 0)
        defineOwnProperty("MAX_VALUE", Double.MAX_VALUE.toValue(), 0)
        defineOwnProperty("MIN_SAFE_INTEGER", (-AOs.MAX_SAFE_INTEGER).toValue(), 0)
        defineOwnProperty("MIN_VALUE", Double.MIN_VALUE.toValue(), 0)
        defineOwnProperty("NaN", Double.NaN.toValue(), 0)
        defineOwnProperty("NEGATIVE_INFINITY", Double.NEGATIVE_INFINITY.toValue(), 0)
        defineOwnProperty("POSITIVE_INFINITY", Double.POSITIVE_INFINITY.toValue(), 0)
        defineBuiltin("isFinite", 1, ::isFinite)
        defineBuiltin("isInteger", 1, ::isInteger)
        defineBuiltin("isNaN", 1, ::isNaN)
        defineBuiltin("isSafeInteger", 1, ::isSafeInteger)
        defineBuiltin("parseFloat", 1, ::parseFloat)
        defineBuiltin("parseInt", 1, ::parseInt)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        val newTarget = arguments.newTarget
        val value = arguments.argument(0)
        val n = if (value != JSUndefined) {
            val prim = value.toNumeric()
            if (prim is JSBigInt) {
                prim.number.toDouble().toValue()
            } else prim as JSNumber
        } else JSNumber.ZERO

        if (newTarget == JSUndefined)
            return n

        return AOs.ordinaryCreateFromConstructor(
            newTarget,
            listOf(Slot.NumberData),
            defaultProto = Realm::numberProto,
        ).also {
            it[Slot.NumberData] = n
        }
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSNumberCtor(realm).initialize()

        @ECMAImpl("20.1.2.2")
        @JvmStatic
        fun isFinite(arguments: JSArguments): JSValue {
            val number = arguments.argument(0)
            if (!number.isNumber)
                return JSFalse
            if (number.isNaN || number.isInfinite)
                return JSFalse
            return JSTrue
        }

        @ECMAImpl("20.1.2.3")
        @JvmStatic
        fun isInteger(arguments: JSArguments): JSValue {
            return AOs.isIntegralNumber(arguments.argument(0)).toValue()
        }

        @ECMAImpl("20.1.2.4")
        @JvmStatic
        fun isNaN(arguments: JSArguments): JSValue {
            return arguments.argument(0).isNaN.toValue()
        }

        @ECMAImpl("20.1.2.5")
        @JvmStatic
        fun isSafeInteger(arguments: JSArguments): JSValue {
            if (!AOs.isIntegralNumber(arguments.argument(0)))
                return JSFalse
            return (abs(arguments.argument(0).asDouble) <= AOs.MAX_SAFE_INTEGER).toValue()
        }

        @ECMAImpl("20.1.2.12")
        @JvmStatic
        fun parseFloat(arguments: JSArguments): JSValue {
            TODO()
        }

        @ECMAImpl("20.1.2.13")
        @JvmStatic
        fun parseInt(arguments: JSArguments): JSValue {
            TODO()
        }
    }
}
