package me.mattco.reeva.runtime.values.functions

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.runtime.environment.EnvRecord
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.primitives.JSNull
import me.mattco.reeva.runtime.values.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.ecmaAssert
import me.mattco.reeva.utils.throwError

abstract class JSFunction(
    realm: Realm,
    val thisMode: ThisMode,
    var envRecord: EnvRecord? = null,
    val homeObject: JSValue = JSUndefined,
    val isClassConstructor: Boolean = false,
    val isStrict: Boolean = false,
    prototype: JSObject = realm.functionProto,
) : JSObject(realm, prototype) {
    open val isCallable: Boolean = true
    open val isConstructable: Boolean = true

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
}
