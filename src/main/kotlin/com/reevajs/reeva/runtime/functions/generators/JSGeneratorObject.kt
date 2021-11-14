package com.reevajs.reeva.runtime.functions.generators

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.core.environment.EnvRecord
import com.reevajs.reeva.interpreter.Interpreter
import com.reevajs.reeva.transformer.TransformedSource
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined

class JSGeneratorObject private constructor(
    realm: Realm,
    val transformedSource: TransformedSource,
    val receiver: JSValue,
    arguments: List<JSValue>,
    val generatorState: Interpreter.GeneratorState,
    var envRecord: EnvRecord,
) : JSObject(realm, realm.generatorObjectProto) {
    private val arguments = listOf(receiver, JSUndefined, generatorState) + arguments

    fun next(value: JSValue): JSValue {
        generatorState.sentValue = value
        return execute()
    }

    fun return_(value: JSValue): JSValue {
        TODO()
    }

    fun throw_(value: JSValue): JSValue {
        TODO()
    }

    private fun execute(): JSValue {
        val interpreter = Interpreter(transformedSource, arguments, envRecord)
        val result = interpreter.interpret()
        return if (result.hasValue) {
            envRecord = interpreter.activeEnvRecord
            Operations.createIterResultObject(
                result.value(),
                generatorState.phase == -1,
            )
        } else throw result.error()
    }

    companion object {
        fun create(
            transformedSource: TransformedSource,
            receiver: JSValue,
            arguments: List<JSValue>,
            generatorState: Interpreter.GeneratorState,
            envRecord: EnvRecord,
            realm: Realm = Agent.activeAgent.getActiveRealm(),
        ) = JSGeneratorObject(realm, transformedSource, receiver, arguments, generatorState, envRecord).initialize()
    }
}
