package com.reevajs.reeva.core.errors

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.ExecutionContext
import com.reevajs.reeva.runtime.JSValue

class ThrowException(val value: JSValue) : Exception() {
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
