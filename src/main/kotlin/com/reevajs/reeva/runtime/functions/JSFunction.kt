package com.reevajs.reeva.runtime.functions

import com.reevajs.reeva.Reeva
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSEmpty
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.ecmaAssert
import com.reevajs.reeva.utils.expect

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
            if (isClassConstructor)
                Errors.Class.CtorRequiresNew.throwTypeError(realm)
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

            val thisValue = if (constructorKind == ConstructorKind.Base) {
                Operations.ordinaryCreateFromConstructor(
                    realm,
                    arguments.newTarget,
                    realm.objectProto,
                )
            } else JSEmpty

            val result = evaluate(arguments.withThisValue(thisValue))
            when {
                result is JSObject -> result
                constructorKind == ConstructorKind.Base -> thisValue
                result != JSUndefined -> Errors.Class.ReturnObjectFromDerivedCtor.throwTypeError(realm)
                else -> thisValue
            }
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
