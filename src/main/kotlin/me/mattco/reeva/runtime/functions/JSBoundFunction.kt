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
        val args = boundArguments + arguments

        if (arguments.newTarget == JSUndefined) {
            // [[Call]]
            return Operations.call(realm, boundTargetFunction, boundArguments.thisValue, args)
        }

        // [[Construct]]
        ecmaAssert(Operations.isConstructor(boundTargetFunction))
        val newTarget = if (this.sameValue(arguments.newTarget)) {
            boundTargetFunction
        } else arguments.newTarget
        return Operations.construct(
            boundTargetFunction,
            args,
            newTarget,
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
