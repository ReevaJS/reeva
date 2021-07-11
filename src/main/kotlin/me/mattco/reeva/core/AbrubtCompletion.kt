package me.mattco.reeva.core

import me.mattco.reeva.runtime.JSValue

sealed class AbruptCompletion : Throwable()

class ThrowException(val value: JSValue) : AbruptCompletion()
