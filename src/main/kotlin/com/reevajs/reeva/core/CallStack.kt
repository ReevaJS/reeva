package com.reevajs.reeva.core

import com.reevajs.reeva.parsing.lexer.SourceLocation
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.utils.expect

class CallStack {
    private var activeFunctionBacker: JSFunction? = null
    private val callStack = ArrayDeque<StackTraceFrame>()
    private var pendingLocation: SourceLocation? = null

    fun setPendingLocation(location: SourceLocation?) {
        pendingLocation = location
    }

    fun pushActiveFunction(function: JSFunction) {
        if (activeFunctionBacker == null) {
            activeFunctionBacker = function
            pendingLocation = null
            return
        }

        val frame = StackTraceFrame(activeFunctionBacker!!, pendingLocation)
        activeFunctionBacker = function
        callStack.addLast(frame)
    }

    fun popActiveFunction() {
        expect(activeFunctionBacker != null)
        activeFunctionBacker = if (callStack.isEmpty()) {
            null
        } else {
            callStack.removeLast().enclosingFunction
        }
    }

    fun hasActiveFunction() = activeFunctionBacker != null

    fun activeFunction(): JSFunction {
        return activeFunctionBacker
            ?: throw IllegalStateException("Attempt to get active function when none exists")
    }

    fun stackFrames(): List<StackTraceFrame> = callStack.toList()
}

data class StackTraceFrame(
    val enclosingFunction: JSFunction,
    val invocationLocation: SourceLocation?,
) {
    val name = enclosingFunction.debugName
    val isNative = enclosingFunction is JSNativeFunction
}