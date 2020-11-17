package me.mattco.reeva.runtime.functions

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.ecmaAssert

class JSBoundFunction private constructor(
    realm: Realm,
    private val boundTargetFunction: JSFunction,
    private val boundThis: JSValue,
    private val boundArguments: JSArguments,
    prototype: JSValue,
) : JSFunction(
    realm,
    boundTargetFunction.thisMode,
    boundTargetFunction.envRecord,
    boundTargetFunction.homeObject,
    boundTargetFunction.isStrict,
    prototype,
) {
    override fun call(thisValue: JSValue, arguments: JSArguments): JSValue {
        return Operations.call(boundTargetFunction, boundThis, boundArguments + arguments)
    }

    override fun construct(arguments: JSArguments, newTarget: JSValue): JSValue {
        ecmaAssert(Operations.isConstructor(boundTargetFunction))
        return Operations.construct(
            boundTargetFunction,
            boundArguments + arguments,
            if (sameValue(newTarget)) boundTargetFunction else newTarget
        )
    }

    companion object {
        fun create(
            realm: Realm,
            boundTargetFunction: JSFunction,
            boundThis: JSValue,
            boundArguments: JSArguments,
            prototype: JSValue,
        ) = JSBoundFunction(realm, boundTargetFunction, boundThis, boundArguments, prototype).initialize()
    }
}
