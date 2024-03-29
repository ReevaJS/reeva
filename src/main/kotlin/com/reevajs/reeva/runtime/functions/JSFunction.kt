package com.reevajs.reeva.runtime.functions

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSEmpty

abstract class JSFunction(
    realm: Realm,
    val debugName: String,
    var thisMode: ThisMode,
    var isStrict: Boolean = false,
    prototype: JSValue = realm.functionProto,
) : JSObject(realm, prototype) {
    open val isCallable: Boolean = true

    var isClassConstructor: Boolean = false
    var constructorKind = ConstructorKind.Base
    var homeObject: JSValue = JSEmpty

    open fun isConstructor(): Boolean {
        // TODO: Consider ThisMode
        return true
    }

    abstract fun call(arguments: JSArguments): JSValue

    abstract fun construct(arguments: JSArguments): JSValue

    fun call(thisValue: JSValue, arguments: List<JSValue>) = call(JSArguments(arguments, thisValue))

    fun construct(newTarget: JSValue, arguments: List<JSValue>) =
        construct(JSArguments(arguments, newTarget = newTarget))

    enum class ConstructorKind {
        Base,
        Derived,
    }

    enum class ThisMode {
        Lexical,
        Strict,
        Global
    }
}
