package me.mattco.reeva.runtime.values.wrappers

import me.mattco.reeva.runtime.Agent.Companion.checkError
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.functions.JSNativeFunction
import me.mattco.reeva.runtime.values.objects.Descriptor
import me.mattco.reeva.runtime.values.primitives.JSFalse
import me.mattco.reeva.runtime.values.primitives.JSTrue
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.argument
import me.mattco.reeva.utils.toValue
import kotlin.math.abs

class JSNumberCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Number", 1) {
    init {
        isConstructable = true
    }

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
    }

    @ECMAImpl("20.1.2.2")
    @JSMethod("isFinite", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun isFinite(thisValue: JSValue, arguments: JSArguments): JSValue {
        val number = arguments.argument(0)
        if (!number.isNumber)
            return JSFalse
        if (number.isNaN || number.isInfinite)
            return JSFalse
        return JSTrue
    }

    @JSThrows
    @ECMAImpl("20.1.2.3")
    @JSMethod("isInteger", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun isInteger(thisValue: JSValue, arguments: JSArguments): JSValue {
        return Operations.isIntegralNumber(arguments.argument(0)).toValue()
    }

    @ECMAImpl("20.1.2.4")
    @JSMethod("isNaN", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun isNaN(thisValue: JSValue, arguments: JSArguments): JSValue {
        return arguments.argument(0).isNaN.toValue()
    }

    @JSThrows
    @ECMAImpl("20.1.2.5")
    @JSMethod("isSafeInteger", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun isSafeInteger(thisValue: JSValue, arguments: JSArguments): JSValue {
        if (!Operations.isIntegralNumber(arguments.argument(0)))
            return JSFalse
        checkError() ?: return INVALID_VALUE
        return (abs(arguments.argument(0).asDouble) <= Operations.MAX_SAFE_INTEGER).toValue()
    }

    @ECMAImpl("20.1.2.12")
    @JSMethod("parseFloat", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun parseFloat(thisValue: JSValue, arguments: JSArguments): JSValue {
        TODO()
    }

    @ECMAImpl("20.1.2.13")
    @JSMethod("parseInt", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun parseInt(thisValue: JSValue, arguments: JSArguments): JSValue {
        TODO()
    }

    @JSThrows
    override fun call(thisValue: JSValue, arguments: JSArguments): JSValue {
        return numberFromArg(arguments.argument(0)).toValue()
    }

    @JSThrows
    override fun construct(arguments: JSArguments, newTarget: JSValue): JSValue {
        // TODO: Handle newTarget?
        return JSNumberObject.create(realm, numberFromArg(arguments.argument(0)).toValue())
    }

    @JSThrows
    private fun numberFromArg(argument: JSValue): Double {
        return if (!argument.isUndefined) {
            val prim = Operations.toNumeric(argument)
            checkError() ?: return 0.0
            if (prim.isBigInt)
                TODO()
            prim.asDouble
        } else 0.0
    }

    companion object {
        fun create(realm: Realm) = JSNumberCtor(realm).also { it.init() }
    }
}
