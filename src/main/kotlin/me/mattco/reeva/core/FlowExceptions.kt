package me.mattco.reeva.core

import me.mattco.reeva.runtime.JSValue

sealed class AbruptCompletion : Throwable()

class BreakException(val label: String?) : AbruptCompletion()

class ContinueException(val label: String?) : AbruptCompletion()

class ReturnException(val value: JSValue) : AbruptCompletion()

class ThrowException(val value: JSValue) : AbruptCompletion()
