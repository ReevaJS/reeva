package me.mattco.jsthing.runtime.values.primitives

import me.mattco.jsthing.runtime.values.JSValue

class JSNumber(val value: Double) : JSValue() {
    constructor(value: Int) : this(value.toDouble())
}
