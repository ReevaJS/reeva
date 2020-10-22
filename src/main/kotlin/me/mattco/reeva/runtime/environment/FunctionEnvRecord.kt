package me.mattco.reeva.runtime.environment

import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.functions.JSFunction
import me.mattco.reeva.compiler.JSScriptFunction
import me.mattco.reeva.runtime.values.primitives.JSNull
import me.mattco.reeva.runtime.values.primitives.JSUndefined

class FunctionEnvRecord(
    val functionObject: JSFunction,
    var thisValue: JSValue,
    var thisBindingStatus: ThisBindingStatus,
    val homeObject: JSValue = JSUndefined,
    val newTarget: JSValue = JSUndefined,
    outerEnv: EnvRecord? = null
) : DeclarativeEnvRecord(outerEnv) {
    init {
        if (!homeObject.isUndefined && !homeObject.isObject)
            throw IllegalArgumentException()
        if (!newTarget.isUndefined && !newTarget.isObject)
            throw IllegalArgumentException()
    }

    @ECMAImpl("BindThisValue", "8.1.1.3.1")
    fun bindThisValue(value: JSValue): JSValue {
        if (thisBindingStatus == ThisBindingStatus.Lexical)
            throw IllegalStateException("Attempt to bind a lexical 'this' value")

        if (thisBindingStatus == ThisBindingStatus.Initialized)
            throw IllegalStateException("Attempt to bind an initialized 'this' value")

        thisValue = value
        thisBindingStatus = ThisBindingStatus.Initialized
        return value
    }

    @ECMAImpl("HasThisBinding", "8.1.1.3.2")
    override fun hasThisBinding() = thisBindingStatus != ThisBindingStatus.Lexical

    @ECMAImpl("HasSuperBinding", "8.1.1.3.3")
    override fun hasSuperBinding() = hasThisBinding() && homeObject.isObject

    @ECMAImpl("GetThisBinding", "8.1.1.3.4")
    fun getThisBinding(): JSValue {
        if (!hasThisBinding())
            throw IllegalStateException("Attempt to get non-bound 'this' value")

        if (thisBindingStatus == ThisBindingStatus.Uninitialized)
            TODO("Throw ReferenceError")

        return thisValue
    }

    @ECMAImpl("GetSuperBase", "8.1.1.3.5")
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
        @ECMAImpl("NewFunctionEnvironment", "8.1.2.4")
        internal fun create(function: JSFunction, newTarget: JSValue): FunctionEnvRecord {
            val thisBindingStatus = if (function.thisMode == JSFunction.ThisMode.Lexical) {
                FunctionEnvRecord.ThisBindingStatus.Lexical
            } else FunctionEnvRecord.ThisBindingStatus.Uninitialized

            return FunctionEnvRecord(
                function,
                JSUndefined,
                thisBindingStatus,
                if (function is JSScriptFunction) function.getHomeObject() else JSNull,
                newTarget,
                function.envRecord
            )
        }
    }
}
