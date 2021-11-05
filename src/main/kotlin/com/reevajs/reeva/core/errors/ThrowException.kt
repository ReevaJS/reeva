package com.reevajs.reeva.core.errors

import com.reevajs.reeva.Reeva
import com.reevajs.reeva.runtime.JSValue

class ThrowException private constructor(
    val value: JSValue,
    val stackTrace: List<StackTraceFrame>,
) : Exception() {
    constructor(value: JSValue) : this(value, Reeva.activeAgent.callStack.toList())
}
