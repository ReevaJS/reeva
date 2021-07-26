package com.reevajs.reeva.core

import com.reevajs.reeva.runtime.JSValue

sealed class AbruptCompletion : Throwable()

class ThrowException(val value: JSValue) : AbruptCompletion()
