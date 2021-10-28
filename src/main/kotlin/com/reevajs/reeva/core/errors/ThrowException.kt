package com.reevajs.reeva.core.errors

import com.reevajs.reeva.runtime.JSValue

class ThrowException(val value: JSValue) : Throwable()
