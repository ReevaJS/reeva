package com.reevajs.reeva.runtime.wrappers

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.builtins.Builtin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.*
import com.reevajs.reeva.utils.toValue
import kotlin.math.abs

class JSNumberCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Number", 1) {
    override fun init() {
        super.init()

        defineOwnProperty("EPSILON", Math.ulp(1.0).toValue(), 0)
        defineOwnProperty("MAX_SAFE_INTEGER", Operations.MAX_SAFE_INTEGER.toValue(), 0)
        defineOwnProperty("MAX_VALUE", Double.MAX_VALUE.toValue(), 0)
        defineOwnProperty("MIN_SAFE_INTEGER", (-Operations.MAX_SAFE_INTEGER).toValue(), 0)
        defineOwnProperty("MIN_VALUE", Double.MIN_VALUE.toValue(), 0)
        defineOwnProperty("NaN", Double.NaN.toValue(), 0)
        defineOwnProperty("NEGATIVE_INFINITY", Double.NEGATIVE_INFINITY.toValue(), 0)
        defineOwnProperty("POSITIVE_INFINITY", Double.POSITIVE_INFINITY.toValue(), 0)
        defineBuiltin("isFinite", 1, Builtin.NumberCtorIsFinite)
        defineBuiltin("isInteger", 1, Builtin.NumberCtorIsInteger)
        defineBuiltin("isNaN", 1, Builtin.NumberCtorIsNaN)
        defineBuiltin("isSafeInteger", 1, Builtin.NumberCtorIsSafeInteger)
        defineBuiltin("parseFloat", 1, Builtin.NumberCtorParseFloat)
        defineBuiltin("parseInt", 1, Builtin.NumberCtorParseInt)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        val newTarget = arguments.newTarget
        val value = arguments.argument(0)
        val n = if (value != JSUndefined) {
            numberFromArg(realm, value).toValue()
        } else JSNumber.ZERO

        if (newTarget == JSUndefined)
            return n

        return Operations.ordinaryCreateFromConstructor(realm, newTarget, realm.numberProto, listOf(SlotName.NumberData)).also {
            it.setSlot(SlotName.NumberData, n)
        }
    }

    private fun numberFromArg(realm: Realm, argument: JSValue): Double {
        return if (!argument.isUndefined) {
            val prim = Operations.toNumeric(realm, argument)
            if (prim is JSBigInt)
                return prim.number.toDouble()
            prim.asDouble
        } else 0.0
    }

    companion object {
        fun create(realm: Realm) = JSNumberCtor(realm).initialize()

        @ECMAImpl("20.1.2.2")
        @JvmStatic
        fun isFinite(realm: Realm, arguments: JSArguments): JSValue {
            val number = arguments.argument(0)
            if (!number.isNumber)
                return JSFalse
            if (number.isNaN || number.isInfinite)
                return JSFalse
            return JSTrue
        }

        @ECMAImpl("20.1.2.3")
        @JvmStatic
        fun isInteger(realm: Realm, arguments: JSArguments): JSValue {
            return Operations.isIntegralNumber(arguments.argument(0)).toValue()
        }

        @ECMAImpl("20.1.2.4")
        @JvmStatic
        fun isNaN(realm: Realm, arguments: JSArguments): JSValue {
            return arguments.argument(0).isNaN.toValue()
        }

        @ECMAImpl("20.1.2.5")
        @JvmStatic
        fun isSafeInteger(realm: Realm, arguments: JSArguments): JSValue {
            if (!Operations.isIntegralNumber(arguments.argument(0)))
                return JSFalse
            return (abs(arguments.argument(0).asDouble) <= Operations.MAX_SAFE_INTEGER).toValue()
        }

        @ECMAImpl("20.1.2.12")
        @JvmStatic
        fun parseFloat(realm: Realm, arguments: JSArguments): JSValue {
            TODO()
        }

        @ECMAImpl("20.1.2.13")
        @JvmStatic
        fun parseInt(realm: Realm, arguments: JSArguments): JSValue {
            TODO()
        }
    }
}
