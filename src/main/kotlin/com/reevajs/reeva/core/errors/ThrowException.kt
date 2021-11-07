package com.reevajs.reeva.core.errors

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.StackTraceFrame
import com.reevajs.reeva.runtime.JSValue

class ThrowException(val value: JSValue) : Exception() {
    val stackFrames: List<StackTraceFrame>

    init {
        Agent.activeAgent.callStack.run {
            pushActiveFunction(activeFunction())
            stackFrames = stackFrames()
            popActiveFunction()
        }
    }
}
