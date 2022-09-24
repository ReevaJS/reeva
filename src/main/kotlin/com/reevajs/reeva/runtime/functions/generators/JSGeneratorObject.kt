package com.reevajs.reeva.runtime.functions.generators

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.ExecutionContext
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.interpreter.Interpreter
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.transformer.TransformedSource

class JSGeneratorObject private constructor(
    realm: Realm,
    val transformedSource: TransformedSource,
    val receiver: JSValue,
    arguments: List<JSValue>,
    val generatorState: Interpreter.GeneratorState,
    private val context: ExecutionContext,
) : JSObject(realm.generatorObjectProto) {
    private val arguments = listOf(receiver, JSUndefined, generatorState) + arguments

    fun next(value: JSValue): JSValue {
        generatorState.sentValue = value
        val agent = Agent.activeAgent

        agent.pushExecutionContext(context)
        return try {
            execute()
        } finally {
            agent.popExecutionContext()
        }
    }

    fun return_(value: JSValue): JSValue {
        TODO()
    }

    fun throw_(value: JSValue): JSValue {
        TODO()
    }

    private fun execute(): JSValue {
        val interpreter = Interpreter(transformedSource, arguments)
        val result = interpreter.interpret()
        return if (result.hasValue) {
            Operations.createIterResultObject(
                result.value(),
                generatorState.phase == -1,
            )
        } else throw result.error()
    }

    companion object {
        fun create(
            realm: Realm,
            transformedSource: TransformedSource,
            receiver: JSValue,
            arguments: List<JSValue>,
            generatorState: Interpreter.GeneratorState,
            executionContext: ExecutionContext,
        ) = JSGeneratorObject(
            realm,
            transformedSource,
            receiver,
            arguments,
            generatorState,
            executionContext,
        ).initialize(realm)
    }
}
