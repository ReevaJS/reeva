package com.reevajs.reeva.runtime.promises

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.errors.ThrowException
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.errors.JSTypeErrorObject
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined

class JSResolveFunction private constructor(
    val promise: JSObject,
    var alreadyResolved: Operations.Wrapper<Boolean>,
    realm: Realm
) : JSNativeFunction(realm, "", 1) {
    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget != JSUndefined)
            TODO("Not yet implemented")
        if (alreadyResolved.value)
            return JSUndefined
        alreadyResolved.value = true

        val resolution = arguments.argument(0)
        if (resolution.sameValue(promise)) {
            val selfResolutionError = JSTypeErrorObject.create(realm, "TODO: message (promise self resolution)")
            return Operations.rejectPromise(promise, selfResolutionError)
        }

        if (resolution !is JSObject)
            return Operations.fulfillPromise(promise, resolution)

        val thenAction = try {
            resolution.get("then")
        } catch (e: ThrowException) {
            return Operations.rejectPromise(promise, e.value)
        }

        if (!Operations.isCallable(thenAction))
            return Operations.fulfillPromise(promise, resolution)

        val thenJobCallback = Agent.activeAgent.hostHooks.makeJobCallback(thenAction)
        val job = Operations.newPromiseResolveThenableJob(promise, resolution, thenJobCallback)
        Agent.activeAgent.hostHooks.enqueuePromiseJob(job.realm, job.job)

        return JSUndefined
    }

    companion object {
        fun create(realm: Realm, promise: JSObject, alreadyResolved: Operations.Wrapper<Boolean>) =
            JSResolveFunction(promise, alreadyResolved, realm).initialize(realm)
    }
}
