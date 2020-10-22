package me.mattco.reeva.runtime.values.primitives

import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.values.JSValue

class JSSymbol(val description: String) : JSValue() {
    @ECMAImpl("SymbolDescritiveString", "19.4.3.3.1")
    fun descriptiveString() = "Symbol($description)"

    override fun toString() = "JSSymbol(description=$description)"
}