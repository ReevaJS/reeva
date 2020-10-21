package me.mattco.renva.runtime.values.functions

import me.mattco.renva.runtime.Realm
import me.mattco.renva.runtime.environment.EnvRecord
import me.mattco.renva.runtime.values.JSValue
import me.mattco.renva.runtime.values.objects.JSObject

typealias NativeFunctionSignature = (thisValue: JSValue, arguments: List<JSValue>) -> JSValue
typealias NativeGetterSignature = () -> JSValue
typealias NativeSetterSignature = (value: JSValue) -> Unit

abstract class JSFunction(
    val realm: Realm,
    val thisMode: ThisMode,
    var envRecord: EnvRecord? = null,
) : JSObject(realm, realm.functionProto) {
    open val isCallable: Boolean = true
    open val isConstructable: Boolean = true

    abstract fun name(): String
    abstract fun call(thisValue: JSValue, arguments: List<JSValue>): JSValue
    abstract fun construct(arguments: List<JSValue>, newTarget: JSObject): JSValue

    enum class ThisMode {
        Lexical,
        Strict,
        Global
    }
}
