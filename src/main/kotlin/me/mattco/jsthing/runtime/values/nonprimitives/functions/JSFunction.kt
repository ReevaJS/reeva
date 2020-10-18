package me.mattco.jsthing.runtime.values.nonprimitives.functions

import me.mattco.jsthing.runtime.Realm
import me.mattco.jsthing.runtime.contexts.ExecutionContext
import me.mattco.jsthing.runtime.environment.EnvRecord
import me.mattco.jsthing.runtime.values.JSValue
import me.mattco.jsthing.runtime.values.nonprimitives.objects.JSObject

typealias NativeFunctionSignature = (context: ExecutionContext, arguments: List<JSValue>) -> JSValue
typealias NativeGetterSignature = (context: ExecutionContext) -> JSValue
typealias NativeSetterSignature = (context: ExecutionContext, value: JSValue) -> Unit

abstract class JSFunction(
    val realm: Realm,
    val thisMode: ThisMode,
    var envRecord: EnvRecord? = null,
) : JSObject(realm, realm.functionProto) {
    abstract fun name(): String
    abstract fun call(context: ExecutionContext, arguments: List<JSValue>): JSValue
    abstract fun construct(context: ExecutionContext, newTarget: JSObject, arguments: List<JSValue>): JSValue

    enum class ThisMode {
        Lexical,
        Strict,
        Global
    }
}
