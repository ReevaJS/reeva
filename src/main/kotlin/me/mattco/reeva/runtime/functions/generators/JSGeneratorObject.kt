package me.mattco.reeva.runtime.functions.generators

import me.mattco.reeva.core.EvaluationResult
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.interpreter.Interpreter
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.expect

/*
function* foo() {
    yield 1;
    yield 2;
    yield 3;
}

1:
    LdaInt 1
    Yield next: @2
2:
    LdaInt 2
    Yield next: @3
3:
    LdaInt 3
    Yield next: @4
4:
 */

class JSGeneratorObject(
    realm: Realm,
    val interpreter: Interpreter,
    val envRecord: EnvRecord,
) : JSObject(realm, realm.generatorObjectProto) {
    fun next(realm: Realm, mode: Interpreter.SuspendedEntryMode, value: JSValue): JSValue {
        return when (val result = interpreter.reenterSuspendedFunction(mode, value)) {
            null -> Operations.createIterResultObject(realm, JSUndefined, true)
            is EvaluationResult.Success -> Operations.createIterResultObject(realm, result.value, false)
            else -> {
                expect(result is EvaluationResult.RuntimeError)
                throw ThrowException(result.value)
            }
        }
    }

    companion object {
        fun create(realm: Realm, interpreter: Interpreter, envRecord: EnvRecord) =
            JSGeneratorObject(realm, interpreter, envRecord).initialize()
    }
}
