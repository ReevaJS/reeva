package com.reevajs.reeva.runtime.promises

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.errors.ThrowException
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.errors.JSTypeErrorObject
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined

class JSResolveFunction private constructor(
    val promise: JSObject,
    var alreadyResolved: AOs.Wrapper<Boolean>,
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
            val selfResolutionError = JSTypeErrorObject.create("TODO: message (promise self resolution)")
            return AOs.rejectPromise(promise, selfResolutionError)
        }

        if (resolution !is JSObject)
            return AOs.fulfillPromise(promise, resolution)

        val thenAction = try {
            resolution.get("then")
        } catch (e: ThrowException) {
            return AOs.rejectPromise(promise, e.value)
        }

        if (!AOs.isCallable(thenAction))
            return AOs.fulfillPromise(promise, resolution)

        val thenJobCallback = Agent.activeAgent.hostHooks.makeJobCallback(thenAction)
        val job = AOs.newPromiseResolveThenableJob(promise, resolution, thenJobCallback)
        Agent.activeAgent.hostHooks.enqueuePromiseJob(job.realm, job.job)

        return JSUndefined
    }

    companion object {
        fun create(
            promise: JSObject,
            alreadyResolved: AOs.Wrapper<Boolean>,
            realm: Realm = Agent.activeAgent.getActiveRealm(),
        ) = JSResolveFunction(promise, alreadyResolved, realm).initialize()
    }
}
