package me.mattco.reeva.runtime.builtins.promises

import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.primitives.JSEmpty
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.argument
import me.mattco.reeva.utils.throwTypeError

class JSPromiseCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Promise", 2) {
    init {
        isConstructable = true
    }

    override fun call(thisValue: JSValue, arguments: JSArguments): JSValue {
        throwTypeError("the Promise constructor must be called with the 'new' keyword")
    }

    override fun construct(arguments: JSArguments, newTarget: JSValue): JSValue {
        val executor = arguments.argument(0)
        if (!Operations.isCallable(executor))
            throwTypeError("TODO: message")

        val promise = JSPromiseObject.create(Operations.PromiseState.Pending, JSEmpty, realm)
        val (resolveFunction, rejectFunction) = Operations.createResolvingFunctions(promise)
        try {
            Operations.call(executor, JSUndefined, listOf(resolveFunction, rejectFunction))
        } catch (e: ThrowException) {
            Operations.call(rejectFunction, JSUndefined, listOf(e.value))
        }
        return promise
    }

    companion object {
        fun create(realm: Realm) = JSPromiseCtor(realm).also { it.init() }
    }
}
