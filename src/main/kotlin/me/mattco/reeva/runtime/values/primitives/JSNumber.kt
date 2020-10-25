package me.mattco.reeva.runtime.values.primitives

import me.mattco.reeva.runtime.values.JSValue

class JSNumber(val number: Double) : JSValue() {
    constructor(value: Int) : this(value.toDouble())
    constructor(value: Number) : this(value.toDouble())
}
