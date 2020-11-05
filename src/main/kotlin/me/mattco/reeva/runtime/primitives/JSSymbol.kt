package me.mattco.reeva.runtime.primitives

import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.JSValue

class JSSymbol(val description: String?) : JSValue() {
    @ECMAImpl("19.4.3.3.1", "SymbolDescriptiveString")
    fun descriptiveString() = "Symbol(${description ?: ""})"

    override fun toString() = "JSSymbol(description=$description)"
}
