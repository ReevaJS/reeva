package me.mattco.reeva.core

import me.mattco.reeva.Reeva
import me.mattco.reeva.runtime.*
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.objects.JSObject

open class HostHooks {
    @ECMAImpl("9.5.2")
    open fun makeJobCallback(callback: JSFunction): Operations.JobCallback {
        return Operations.JobCallback(callback)
    }

    @ECMAImpl("9.5.3")
    open fun callJobCallback(realm: Realm, handler: Operations.JobCallback, arguments: JSArguments): JSValue {
        return Operations.call(realm, handler.callback, arguments)
    }

    @ECMAImpl("9.5.4")
    open fun enqueuePromiseJob(job: () -> Unit, realm: Realm?) {
        Reeva.activeAgent.addMicrotask(job)
    }

    /**
     * Used to allow the runtime-evaluation of user-provided strings. If such
     * behavior is not desired, this function may be overridden to throw an
     * exception, which will be reflected in eval and the various Function
     * constructors.
     */
    @ECMAImpl("19.2.1.2")
    open fun ensureCanCompileStrings(callerRealm: Realm, calleeRealm: Realm) {
    }

    @ECMAImpl("27.2.1.9")
    open fun promiseRejectionTracker(realm: Realm, promise: JSObject, operation: String) {
        if (operation == "reject") {
            Reeva.activeAgent.addMicrotask {
                // If promise does not have any handlers by the time this microtask is ran, it
                // will not have any handlers, and we can print a warning
                if (!promise.getSlotAs<Boolean>(SlotName.PromiseIsHandled)) {
                    val result = promise.getSlotAs<JSValue>(SlotName.PromiseResult)
                    println("\u001b[31mUnhandled promise rejection: ${Operations.toString(realm, result)}\u001B[0m")
                }
            }
        }
    }
}
