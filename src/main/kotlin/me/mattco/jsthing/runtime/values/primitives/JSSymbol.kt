package me.mattco.jsthing.runtime.values.primitives

import me.mattco.jsthing.runtime.values.JSValue

class JSSymbol(val description: String?) : JSValue() {
    override fun toString() = "JSSymbol(description=$description)"
}
