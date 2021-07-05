package me.mattco.reeva.runtime.functions

import me.mattco.reeva.Reeva
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSEmpty
import me.mattco.reeva.utils.ecmaAssert
import me.mattco.reeva.utils.expect

abstract class JSFunction(
    realm: Realm,
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

    abstract fun evaluate(arguments: JSArguments): JSValue

    fun call(arguments: JSArguments): JSValue {
        return Reeva.activeAgent.inCallScope(this) {
            // TODO: Should this throw an error? Or will we never get here to due
            // the guard in Operations.call
            expect(isCallable)
            evaluate(arguments)
        }
    }

    fun call(thisValue: JSValue, arguments: List<JSValue>): JSValue {
        return call(JSArguments(arguments, thisValue))
    }

    fun construct(arguments: JSArguments): JSValue {
        return Reeva.activeAgent.inCallScope(this) {
            // TODO: Should this throw an error? Or will we never get here to due
            // the guard in Operations.construct
            expect(isConstructor())

            ecmaAssert(arguments.newTarget is JSObject)

            val thisValue = Operations.ordinaryCreateFromConstructor(
                realm,
                arguments.newTarget,
                realm.objectProto,
            )

            val result = evaluate(arguments.withThisValue(thisValue))
            if (result is JSObject) result else thisValue
        }
    }

    fun construct(newTarget: JSValue, arguments: List<JSValue>): JSValue {
        return construct(JSArguments(arguments, newTarget = newTarget))
    }

    enum class ConstructorKind {
        Base,
        Derived,
    }

    enum class ThisMode {
        Lexical,
        NonLexical,
        Strict,
        Global
    }
}
