package me.mattco.reeva.core

import me.mattco.reeva.runtime.JSValue

sealed class FlowException : Throwable()

class BreakException(val label: String?) : FlowException()

class ContinueException(val label: String?) : FlowException()

class ReturnException(val value: JSValue) : FlowException()

class ThrowException(val value: JSValue) : FlowException()
