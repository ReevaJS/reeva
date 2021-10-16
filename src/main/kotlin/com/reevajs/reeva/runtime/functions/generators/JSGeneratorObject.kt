package com.reevajs.reeva.runtime.functions.generators

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.ThrowException
import com.reevajs.reeva.core.environment.EnvRecord
import com.reevajs.reeva.core.lifecycle.Executable
import com.reevajs.reeva.core.lifecycle.ExecutionResult
import com.reevajs.reeva.interpreter.Interpreter
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.unreachable

class JSGeneratorObject private constructor(
    realm: Realm,
    val receiver: JSValue,
    arguments: List<JSValue>,
    val executable: Executable,
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
        val interpreter = Interpreter(realm, executable, arguments, envRecord)
        when (val result = interpreter.interpret()) {
            is ExecutionResult.Success -> {
                envRecord = interpreter.activeEnvRecord
                return Operations.createIterResultObject(
                    realm,
                    result.value,
                    generatorState.phase == -1,
                )
            }
            is ExecutionResult.InternalError -> throw result.cause
            is ExecutionResult.RuntimeError -> throw ThrowException(result.value)
            is ExecutionResult.ParseError -> unreachable()
        }
    }

    companion object {
        fun create(
            realm: Realm,
            receiver: JSValue,
            arguments: List<JSValue>,
            executable: Executable,
            generatorState: Interpreter.GeneratorState,
            envRecord: EnvRecord,
        ) = JSGeneratorObject(realm, receiver, arguments, executable, generatorState, envRecord).initialize()
    }
}
