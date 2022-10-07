package com.reevajs.reeva.runtime.functions.generators

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.ExecutionContext
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.interpreter.Interpreter
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.transformer.TransformedSource

class JSGeneratorObject private constructor(
    realm: Realm,
    val transformedSource: TransformedSource,
    val receiver: JSValue,
    arguments: List<JSValue>,
    private val context: ExecutionContext,
) : JSObject(realm, realm.generatorObjectProto) {
    private val interpreter = Interpreter(transformedSource, listOf(receiver, JSUndefined) + arguments)

    fun next(value: JSValue): JSValue {
        return execute(value, Interpreter.YieldContinuation.Continue)
    }

    fun return_(value: JSValue): JSValue {
        return execute(value, Interpreter.YieldContinuation.Return)
    }

    fun throw_(value: JSValue): JSValue {
        return execute(value, Interpreter.YieldContinuation.Throw)
    }

    private fun execute(value: JSValue, mode: Interpreter.YieldContinuation): JSValue {
        val agent = Agent.activeAgent

        agent.pushExecutionContext(context)
        try {
            return AOs.createIterResultObject(interpreter.interpretWithYieldContinuation(value, mode), interpreter.isDone)
        } finally {
            agent.popExecutionContext()
        }
    }

    companion object {
        fun create(
            transformedSource: TransformedSource,
            receiver: JSValue,
            arguments: List<JSValue>,
            executionContext: ExecutionContext,
            realm: Realm = Agent.activeAgent.getActiveRealm(),
        ) = JSGeneratorObject(
            realm,
            transformedSource,
            receiver,
            arguments,
            executionContext,
        ).initialize()
    }
}
