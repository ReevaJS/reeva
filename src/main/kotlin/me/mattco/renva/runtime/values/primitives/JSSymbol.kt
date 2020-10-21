package me.mattco.renva.runtime.values.primitives

import me.mattco.renva.runtime.values.JSValue

class JSSymbol(val description: String?) : JSValue() {
    override fun toString() = "JSSymbol(description=$description)"
}
