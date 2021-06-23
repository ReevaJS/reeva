package me.mattco.reeva.runtime.wrappers

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.SlotName
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.primitives.*
import me.mattco.reeva.utils.toValue
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
        defineNativeFunction("isFinite", 1, ::isFinite)
        defineNativeFunction("isInteger", 1, ::isInteger)
        defineNativeFunction("isNaN", 1, ::isNaN)
        defineNativeFunction("isSafeInteger", 1, ::isSafeInteger)
        defineNativeFunction("parseFloat", 1, ::parseFloat)
        defineNativeFunction("parseInt", 1, ::parseInt)
    }

    @ECMAImpl("20.1.2.2")
    fun isFinite(realm: Realm, arguments: JSArguments): JSValue {
        val number = arguments.argument(0)
        if (!number.isNumber)
            return JSFalse
        if (number.isNaN || number.isInfinite)
            return JSFalse
        return JSTrue
    }

    @ECMAImpl("20.1.2.3")
    fun isInteger(realm: Realm, arguments: JSArguments): JSValue {
        return Operations.isIntegralNumber(arguments.argument(0)).toValue()
    }

    @ECMAImpl("20.1.2.4")
    fun isNaN(realm: Realm, arguments: JSArguments): JSValue {
        return arguments.argument(0).isNaN.toValue()
    }

    @ECMAImpl("20.1.2.5")
    fun isSafeInteger(realm: Realm, arguments: JSArguments): JSValue {
        if (!Operations.isIntegralNumber(arguments.argument(0)))
            return JSFalse
        return (abs(arguments.argument(0).asDouble) <= Operations.MAX_SAFE_INTEGER).toValue()
    }

    @ECMAImpl("20.1.2.12")
    fun parseFloat(realm: Realm, arguments: JSArguments): JSValue {
        TODO()
    }

    @ECMAImpl("20.1.2.13")
    fun parseInt(realm: Realm, arguments: JSArguments): JSValue {
        TODO()
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
    }
}
