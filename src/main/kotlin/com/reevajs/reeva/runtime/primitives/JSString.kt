package com.reevajs.reeva.runtime.primitives

import com.reevajs.reeva.runtime.JSValue

class JSString(val string: String) : JSValue() {
    override fun toString() = string

    companion object {
        val EMPTY = JSString("")
    }
}
