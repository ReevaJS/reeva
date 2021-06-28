package me.mattco.reeva.runtime.functions.generators

import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.interpreter.Interpreter
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined

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
    fun next(realm: Realm, mode: Interpreter.GeneratorEntryMode, value: JSValue): JSValue {
        return realm.withEnv(envRecord) {
            val result = interpreter.reenterGeneratorFunction(mode, value)
            if (result == null) {
                Operations.createIterResultObject(realm, JSUndefined, true)
            } else Operations.createIterResultObject(realm, result, false)
        }
    }

    companion object {
        fun create(realm: Realm, interpreter: Interpreter, envRecord: EnvRecord) =
            JSGeneratorObject(realm, interpreter, envRecord).initialize()
    }
}
