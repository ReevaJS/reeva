package me.mattco.reeva.runtime.functions

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.ecmaAssert

class JSBoundFunction private constructor(
    realm: Realm,
    private val boundTargetFunction: JSFunction,
    private val boundArguments: JSArguments,
    prototype: JSValue,
) : JSFunction(
    realm,
    boundTargetFunction.isStrict,
    prototype,
) {
    override fun isConstructor() = boundTargetFunction.isConstructor()

    override fun evaluate(arguments: JSArguments): JSValue {
        val newArguments = JSArguments(boundArguments + arguments, boundArguments.thisValue)
        val newTarget = arguments.newTarget
        if (newTarget == JSUndefined)
            return Operations.call(realm, boundTargetFunction, newArguments)
        ecmaAssert(Operations.isConstructor(boundTargetFunction))
        return Operations.construct(
            boundTargetFunction,
            newArguments,
            if (sameValue(newTarget)) boundTargetFunction else newTarget
        )
    }

    companion object {
        fun create(
            realm: Realm,
            boundTargetFunction: JSFunction,
            arguments: JSArguments,
            prototype: JSValue,
        ) = JSBoundFunction(realm, boundTargetFunction, arguments, prototype).initialize()
    }
}
