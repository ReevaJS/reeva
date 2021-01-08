package me.mattco.reeva.runtime.functions

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.primitives.JSUndefined
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
    init {
        isCallable = boundTargetFunction.isCallable
        isConstructable = boundTargetFunction.isConstructable
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        val newTarget = super.newTarget
        if (newTarget == JSUndefined)
            return Operations.call(boundTargetFunction, boundThis, boundArguments + arguments)
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
