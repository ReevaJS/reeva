package com.reevajs.reeva.core.errors

import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.errors.JSErrorObject
import com.reevajs.reeva.utils.Result

class ThrowException(val value: JSValue) : Exception((value as? JSErrorObject)?.message) {
    // val stackFrames: List<ExecutionContext>
    //
    // init {
    //     Agent.activeAgent.callStack.run {
    //         pushActiveFunction(activeFunction())
    //         stackFrames = contextStack().asReversed()
    //         popActiveFunction()
    //     }
    // }
}

typealias Completion<T> = Result<ThrowException, T>

fun <T : Any?> completion(block: () -> T): Completion<T> = try {
    Completion.success(block())
} catch (e: ThrowException) {
    Completion.error(e)
}
