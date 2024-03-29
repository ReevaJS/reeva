package com.reevajs.reeva.runtime.primitives

import com.reevajs.reeva.runtime.JSValue

sealed class JSBoolean(val boolean: Boolean) : JSValue() {
    fun inv() = if (this is JSTrue) JSFalse else JSTrue

    companion object {
        fun valueOf(bool: Boolean) = if (bool) JSTrue else JSFalse
    }
}

object JSTrue : JSBoolean(true)

object JSFalse : JSBoolean(false)
