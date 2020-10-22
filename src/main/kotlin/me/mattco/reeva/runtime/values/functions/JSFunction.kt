package me.mattco.reeva.runtime.values.functions

import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.environment.EnvRecord
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.objects.JSObject

abstract class JSFunction(
    realm: Realm,
    val thisMode: ThisMode,
    var envRecord: EnvRecord? = null,
) : JSObject(realm, realm.functionProto) {
    open val isCallable: Boolean = true
    open val isConstructable: Boolean = true

    abstract fun name(): String
    abstract fun call(thisValue: JSValue, arguments: List<JSValue>): JSValue
    abstract fun construct(arguments: List<JSValue>, newTarget: JSValue): JSValue

    enum class ThisMode {
        Lexical,
        NonLexical,
        Strict,
        Global
    }
}
