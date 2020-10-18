package me.mattco.jsthing.runtime

import me.mattco.jsthing.runtime.contexts.ExecutionContext
import me.mattco.jsthing.runtime.environment.FunctionEnvRecord
import me.mattco.jsthing.runtime.environment.GlobalEnvRecord
import me.mattco.jsthing.runtime.values.JSValue
import me.mattco.jsthing.runtime.values.nonprimitives.arrays.JSArrayProto
import me.mattco.jsthing.runtime.values.nonprimitives.functions.JSFunction
import me.mattco.jsthing.runtime.values.nonprimitives.functions.JSFunctionProto
import me.mattco.jsthing.runtime.values.nonprimitives.objects.JSObjectProto
import me.mattco.jsthing.runtime.values.primitives.JSUndefined
import me.mattco.jsthing.utils.ecmaAssert

class Realm(private val agent: Agent) {
    var globalObject: JSValue = JSUndefined
    var globalEnv: GlobalEnvRecord? = null

    lateinit var objectProto: JSObjectProto
    lateinit var arrayProto: JSArrayProto
    lateinit var functionProto: JSFunctionProto

    fun init() {
        objectProto = JSObjectProto.create(this)
        arrayProto = JSArrayProto.create(this)
        functionProto = JSFunctionProto.create(this)
    }

    fun call(function: JSFunction, arguments: List<JSValue> = emptyList(), thisArg: JSValue = JSUndefined): JSValue {
        // TODO: [[IsClassConstructor]]
        val callerContext = agent.runningContext
        val calleeContext = prepareForOrdinaryCall(function, JSUndefined)
        ecmaAssert(agent.runningContext == calleeContext)
        ordinaryCallBindThis(function, calleeContext, thisArg)
        val result = ordinaryCallEvaluateBody(function, arguments)
        agent.popContext()
        return result
    }

    private fun prepareForOrdinaryCall(function: JSFunction, newTarget: JSValue): ExecutionContext {
        ecmaAssert(newTarget.isUndefined || newTarget.isObject)
        val calleeContext = ExecutionContext(agent, function.realm, function)
        val localEnv = FunctionEnvRecord.create(function, newTarget)
        calleeContext.lexicalEnv = localEnv
        calleeContext.variableEnv = localEnv
        // TODO: Suspend?
        return calleeContext.also(agent::addContext)
    }

    private fun ordinaryCallBindThis(function: JSFunction, calleeContext: ExecutionContext, thisArgument: JSValue): JSValue {
        if (function.thisMode == JSFunction.ThisMode.Lexical)
            return JSUndefined

        val thisValue = when {
            function.thisMode == JSFunction.ThisMode.Strict -> thisArgument
            thisArgument.isNullish -> function.realm.globalEnv.let {
                ecmaAssert(it != null)
                it.globalThis
            }
            else -> thisArgument.toObject()
        }

        val localEnv = calleeContext.lexicalEnv
        ecmaAssert(localEnv is FunctionEnvRecord)
        ecmaAssert(localEnv.thisBindingStatus != FunctionEnvRecord.ThisBindingStatus.Initialized)
        return localEnv.bindThisValue(thisValue)
    }

    private fun ordinaryCallEvaluateBody(function: JSFunction, arguments: List<JSValue>): JSValue {
        return function.call(agent.runningContext, arguments)
    }
}
