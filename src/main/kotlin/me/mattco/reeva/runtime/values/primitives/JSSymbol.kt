package me.mattco.reeva.runtime.values.primitives

import me.mattco.reeva.runtime.values.JSValue

class JSSymbol(val description: String?) : JSValue() {
    override fun toString() = "JSSymbol(description=$description)"
}
