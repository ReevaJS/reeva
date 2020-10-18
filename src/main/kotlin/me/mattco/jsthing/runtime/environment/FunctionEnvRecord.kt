package me.mattco.jsthing.runtime.environment

import me.mattco.jsthing.runtime.annotations.ECMAImpl
import me.mattco.jsthing.runtime.values.JSValue
import me.mattco.jsthing.runtime.values.nonprimitives.functions.JSFunction
import me.mattco.jsthing.runtime.values.nonprimitives.functions.JSScriptFunction
import me.mattco.jsthing.runtime.values.primitives.JSNull
import me.mattco.jsthing.runtime.values.primitives.JSUndefined

class FunctionEnvRecord(
    val functionObject: JSFunction,
    var thisValue: JSValue,
    var thisBindingStatus: ThisBindingStatus,
    val homeObject: JSValue = JSNull,
    val newTarget: JSValue = JSNull,
    outerEnv: EnvRecord? = null
) : DeclarativeEnvRecord(outerEnv) {
    init {
        if (!homeObject.isNull && !homeObject.isObject)
            throw IllegalArgumentException()
        if (!newTarget.isNull && !newTarget.isObject)
            throw IllegalArgumentException()
    }

    fun bindThisValue(value: JSValue): JSValue {
        if (thisBindingStatus == ThisBindingStatus.Lexical)
            throw IllegalStateException("Attempt to bind a lexical 'this' value")

        if (thisBindingStatus == ThisBindingStatus.Initialized)
            throw IllegalStateException("Attempt to bind an initialized 'this' value")

        thisValue = value
        thisBindingStatus = ThisBindingStatus.Initialized
        return value
    }

    override fun hasThisBinding() = thisBindingStatus != ThisBindingStatus.Lexical

    override fun hasSuperBinding() = hasThisBinding() && homeObject.isObject

    fun getThisBinding(): JSValue {
        if (!hasThisBinding())
            throw IllegalStateException("Attempt to get non-bound 'this' value")

        if (thisBindingStatus == ThisBindingStatus.Uninitialized)
            TODO("Throw ReferenceError")

        return thisValue
    }

    fun getSuperBase(): JSValue {
        if (homeObject == JSNull)
            return JSUndefined

        TODO("return homeObject.[[GetPrototypeOf]]()")
    }

    enum class ThisBindingStatus {
        Lexical,
        Initialized,
        Uninitialized
    }

    companion object {
        internal fun create(function: JSFunction, newTarget: JSValue): FunctionEnvRecord {
            val thisBindingStatus = if (function.thisMode == JSFunction.ThisMode.Lexical) {
                FunctionEnvRecord.ThisBindingStatus.Lexical
            } else FunctionEnvRecord.ThisBindingStatus.Uninitialized

            return FunctionEnvRecord(
                function,
                JSUndefined,
                thisBindingStatus,
                if (function is JSScriptFunction) function.homeObject else JSNull,
                newTarget,
                function.envRecord
            )
        }
    }
}
