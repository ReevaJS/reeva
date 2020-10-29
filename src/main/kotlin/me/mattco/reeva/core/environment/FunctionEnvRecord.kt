package me.mattco.reeva.core.environment

import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.runtime.errors.JSReferenceErrorObject
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.throwError

class FunctionEnvRecord(
    val function: JSFunction,
    var thisValue: JSValue,
    var thisBindingStatus: ThisBindingStatus,
    val newTarget: JSValue = JSUndefined,
    outerEnv: EnvRecord? = null
) : DeclarativeEnvRecord(outerEnv) {
    init {
        if (!function.homeObject.isUndefined && !function.homeObject.isObject)
            throw IllegalArgumentException()
        if (!newTarget.isUndefined && !newTarget.isObject)
            throw IllegalArgumentException()
    }

    @ECMAImpl("8.1.1.3.1")
    fun bindThisValue(value: JSValue): JSValue {
        if (thisBindingStatus == ThisBindingStatus.Lexical)
            throw IllegalStateException("Attempt to bind a lexical 'this' value")

        if (thisBindingStatus == ThisBindingStatus.Initialized)
            throw IllegalStateException("Attempt to bind an initialized 'this' value")

        thisValue = value
        thisBindingStatus = ThisBindingStatus.Initialized
        return value
    }

    @ECMAImpl("8.1.1.3.2")
    override fun hasThisBinding() = thisBindingStatus != ThisBindingStatus.Lexical

    @ECMAImpl("8.1.1.3.3")
    override fun hasSuperBinding() = hasThisBinding() && function.homeObject.isObject

    @JSThrows
    @ECMAImpl("8.1.1.3.4")
    fun getThisBinding(): JSValue {
        if (!hasThisBinding()) {
            throwError<JSReferenceErrorObject>("current context has no 'this' binding")
            return JSValue.INVALID_VALUE
        }

        if (thisBindingStatus == ThisBindingStatus.Uninitialized)
            TODO("Throw ReferenceError")

        return thisValue
    }

    @ECMAImpl("8.1.1.3.5")
    fun getSuperBase(): JSValue {
        if (function.homeObject == JSUndefined)
            return JSUndefined

        return (function.homeObject as JSObject).getPrototype()
    }

    enum class ThisBindingStatus {
        Lexical,
        Initialized,
        Uninitialized
    }

    companion object {
        @JvmStatic @ECMAImpl("8.1.2.4", "NewFunctionEnvironment")
        fun create(function: JSFunction, newTarget: JSValue): FunctionEnvRecord {
            val thisBindingStatus = if (function.thisMode == JSFunction.ThisMode.Lexical) {
                ThisBindingStatus.Lexical
            } else ThisBindingStatus.Uninitialized

            return FunctionEnvRecord(
                function,
                JSUndefined,
                thisBindingStatus,
                newTarget,
                function.envRecord
            )
        }
    }
}
