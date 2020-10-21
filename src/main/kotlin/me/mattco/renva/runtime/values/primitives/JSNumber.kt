package me.mattco.renva.runtime.values.primitives

import me.mattco.renva.runtime.values.JSValue

class JSNumber(val number: Double) : JSValue() {
    constructor(value: Int) : this(value.toDouble())
}
