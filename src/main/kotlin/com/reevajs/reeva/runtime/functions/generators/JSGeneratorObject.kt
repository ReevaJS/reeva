package com.reevajs.reeva.runtime.functions.generators

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.environment.EnvRecord
import com.reevajs.reeva.interpreter.Interpreter
import com.reevajs.reeva.runtime.objects.JSObject

class JSGeneratorObject(
    realm: Realm,
    val interpreter: Interpreter,
    val envRecord: EnvRecord,
) : JSObject(realm, realm.generatorObjectProto) {
    // TODO: Generators
    // fun next(realm: Realm, mode: Interpreter.SuspendedEntryMode, value: JSValue): JSValue {
    //     return when (val result = interpreter.reenterSuspendedFunction(mode, value)) {
    //         null -> Operations.createIterResultObject(realm, JSUndefined, true)
    //         is ExecutionResult.Success -> Operations.createIterResultObject(realm, result.value, false)
    //         else -> {
    //             expect(result is ExecutionResult.RuntimeError)
    //             throw ThrowException(result.value)
    //         }
    //     }
    // }

    companion object {
        fun create(realm: Realm, interpreter: Interpreter, envRecord: EnvRecord) =
            JSGeneratorObject(realm, interpreter, envRecord).initialize()
    }
}
