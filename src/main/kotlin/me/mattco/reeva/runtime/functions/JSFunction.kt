package me.mattco.reeva.runtime.functions

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined

abstract class JSFunction(
    realm: Realm,
    val thisMode: ThisMode,
    var envRecord: EnvRecord? = null,
    val homeObject: JSValue = JSUndefined,
    val isClassConstructor: Boolean = false,
    val isStrict: Boolean = false,
    prototype: JSObject = realm.functionProto,
) : JSObject(realm, prototype) {
    var constructorKind = ConstructorKind.Base

    var isCallable: Boolean = true
    var isConstructable: Boolean = false

    @JSThrows
    abstract fun call(thisValue: JSValue, arguments: List<JSValue>): JSValue

    @JSThrows
    abstract fun construct(arguments: List<JSValue>, newTarget: JSValue): JSValue

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
}
