package me.mattco.reeva.runtime.functions

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.interpreter.Interpreter
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSEmpty
import me.mattco.reeva.runtime.primitives.JSFalse
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.key

abstract class JSFunction(
    realm: Realm,
    val thisMode: ThisMode,
    var envRecord: EnvRecord? = null,
    var homeObject: JSValue = JSUndefined,
    var isStrict: Boolean = false,
    prototype: JSValue = realm.functionProto,
) : JSObject(realm, prototype) {
    var constructorKind = ConstructorKind.Base

    var isCallable: Boolean = true
    var isConstructable: Boolean = false
    var isClassConstructor: Boolean = false
    var fields: List<FieldRecord> = listOf()

    @JSThrows
    abstract fun call(thisValue: JSValue, arguments: JSArguments): JSValue

    @JSThrows
    abstract fun construct(arguments: JSArguments, newTarget: JSValue): JSValue

    data class FieldRecord(
        val name: JSValue,
        val initializer: JSValue,
        val isAnonymousFunctionDefinition: Boolean,
    )

    enum class ThisMode {
        Lexical,
        NonLexical,
        Strict,
        Global
    }

    enum class ConstructorKind {
        Base,
        Derived,
    }

    companion object {
        fun initializeInstanceFields(obj: JSObject, ctor: JSFunction) {
            ctor.fields.forEach {
                defineField(obj, it)
            }
        }

        private fun defineField(receiver: JSObject, fieldRecord: FieldRecord) {
            val initValue = if (fieldRecord.initializer != JSEmpty) {
                Operations.call(fieldRecord.initializer, receiver)
            } else JSUndefined

            if (fieldRecord.isAnonymousFunctionDefinition) {
                if (Operations.hasOwnProperty(initValue, "name".key()) == JSFalse)
                    Operations.setFunctionName(initValue as JSFunction, Operations.toPropertyKey(fieldRecord.name))
            }

            Operations.createDataPropertyOrThrow(receiver, fieldRecord.name, initValue)
        }
    }
}
