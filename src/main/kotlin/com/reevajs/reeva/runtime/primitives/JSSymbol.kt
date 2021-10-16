package com.reevajs.reeva.runtime.primitives

import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.annotations.ECMAImpl

class JSSymbol(val description: String?) : JSValue() {
    @ECMAImpl("19.4.3.3.1", "SymbolDescriptiveString")
    fun descriptiveString() = "Symbol(${description ?: ""})"

    override fun toString() = "JSSymbol(description=$description)"
}
