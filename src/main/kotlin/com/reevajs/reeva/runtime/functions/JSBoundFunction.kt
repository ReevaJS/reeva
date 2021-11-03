package com.reevajs.reeva.runtime.functions

import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.ecmaAssert

class JSBoundFunction private constructor(
    realm: Realm,
    private val boundTargetFunction: JSFunction,
    private val boundArguments: JSArguments,
    prototype: JSValue,
) : JSFunction(
    realm,
    boundTargetFunction.debugName,
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
