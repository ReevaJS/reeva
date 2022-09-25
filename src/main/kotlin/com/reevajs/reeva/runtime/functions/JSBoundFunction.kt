package com.reevajs.reeva.runtime.functions

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.utils.ecmaAssert

class JSBoundFunction private constructor(
    realm: Realm,
    val boundTargetFunction: JSFunction,
    private val boundArguments: JSArguments,
    prototype: JSValue,
) : JSFunction(
    realm,
    boundTargetFunction.debugName,
    boundTargetFunction.thisMode,
    boundTargetFunction.isStrict,
    prototype,
) {
    override fun isConstructor() = boundTargetFunction.isConstructor()

    @ECMAImpl("10.4.1.1")
    override fun call(arguments: JSArguments): JSValue {
        // 1.  Let target be F.[[BoundTargetFunction]].
        // 2.  Let boundThis be F.[[BoundThis]].
        // 3.  Let boundArgs be F.[[BoundArguments]].
        // 4.  Let args be the list-concatenation of boundArgs and argumentsList.
        val boundThis = boundArguments.thisValue
        val args = boundArguments + arguments

        // 5.  Return ? Call(target, boundThis, args).
        return AOs.call(boundTargetFunction, boundThis, args)
    }

    override fun construct(arguments: JSArguments): JSValue {
        // 1.  Let target be F.[[BoundTargetFunction]].
        // 2.  Assert: IsConstructor(target) is true.
        ecmaAssert(AOs.isConstructor(boundTargetFunction))

        // 3.  Let boundArgs be F.[[BoundArguments]]
        // 4.  Let args be the list-concatenation of boundArgs and argumentsList
        val args = boundArguments + arguments

        // 5.  If SameValue(F, newTarget) is true, set newTarget to target
        val newTarget = if (this.sameValue(arguments.newTarget)) {
            boundTargetFunction
        } else arguments.newTarget

        // 6.  Return ? Construct(target, args, newTarget)
        return AOs.construct(boundTargetFunction, args, newTarget)
    }

    companion object {
        fun create(
            boundTargetFunction: JSFunction,
            arguments: JSArguments,
            prototype: JSValue,
            realm: Realm = Agent.activeAgent.getActiveRealm(),
        ) = JSBoundFunction(realm, boundTargetFunction, arguments, prototype).initialize()
    }
}
