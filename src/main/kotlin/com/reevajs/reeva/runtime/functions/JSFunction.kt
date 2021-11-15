package com.reevajs.reeva.runtime.functions

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSEmpty
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.ecmaAssert
import com.reevajs.reeva.utils.expect

abstract class JSFunction(
    val realm: Realm,
    val debugName: String,
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

    protected abstract fun evaluate(arguments: JSArguments): JSValue

    open fun call(arguments: JSArguments): JSValue {
        // TODO: Should this throw an error? Or will we never get here to due
        // the guard in Operations.call
        expect(isCallable)
        if (isClassConstructor)
            Errors.Class.CtorRequiresNew.throwTypeError(Agent.activeAgent.getActiveRealm())

        return evaluate(arguments)
    }

    fun call(thisValue: JSValue, arguments: List<JSValue>): JSValue {
        return call(JSArguments(arguments, thisValue))
    }

    open fun construct(arguments: JSArguments): JSValue {
        // TODO: Should this throw an error? Or will we never get here to due
        // the guard in Operations.construct
        expect(isConstructor())
        ecmaAssert(arguments.newTarget is JSObject)

        return evaluate(arguments)

        // val thisValue = if (constructorKind == ConstructorKind.Base) {
        //     Operations.ordinaryCreateFromConstructor(
        //         realm,
        //         arguments.newTarget,
        //         realm.objectProto,
        //     )
        // } else JSEmpty
        //
        // val result = evaluate(arguments.withThisValue(thisValue))
        // return when {
        //     result is JSObject -> result
        //     constructorKind == ConstructorKind.Base -> thisValue
        //     result != JSUndefined -> Errors.Class.ReturnObjectFromDerivedCtor.throwTypeError()
        //     else -> thisValue
        // }
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
