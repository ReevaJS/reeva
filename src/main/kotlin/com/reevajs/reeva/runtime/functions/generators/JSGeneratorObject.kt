package com.reevajs.reeva.runtime.functions.generators

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

    fun next(realm: Realm, value: JSValue): JSValue {
        generatorState.sentValue = value
        return execute(realm)
    }

    fun `return`(realm: Realm, value: JSValue): JSValue {
        TODO()
    }

    fun `throw`(realm: Realm, value: JSValue): JSValue {
        TODO()
    }

    private fun execute(realm: Realm): JSValue {
        val interpreter = Interpreter(realm, transformedSource, arguments, envRecord)
        val result = interpreter.interpret()
        return if (result.hasValue) {
            envRecord = interpreter.activeEnvRecord
            Operations.createIterResultObject(
                realm,
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
            envRecord: EnvRecord,
        ) = JSGeneratorObject(realm, transformedSource, receiver, arguments, generatorState, envRecord).initialize()
    }
}
