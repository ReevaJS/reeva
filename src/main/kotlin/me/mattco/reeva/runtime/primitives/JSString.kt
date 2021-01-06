package me.mattco.reeva.runtime.primitives

import me.mattco.reeva.runtime.JSValue

class JSString(val string: String) : JSValue() {
    override fun toString() = string

    companion object {
        val EMPTY = JSString("")
    }
}
