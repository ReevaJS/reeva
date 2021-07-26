package com.reevajs.reeva.runtime.primitives

import com.reevajs.reeva.runtime.JSValue

class JSNumber(val number: Double) : JSValue() {
    constructor(value: Int) : this(value.toDouble())
    constructor(value: Number) : this(value.toDouble())

    companion object {
        val ZERO = JSNumber(0.0)
        val NEGATIVE_ZERO = JSNumber(1.0 / Double.NEGATIVE_INFINITY)
        val NaN = JSNumber(Double.NaN)
        val POSITIVE_INFINITY = JSNumber(Double.POSITIVE_INFINITY)
        val NEGATIVE_INFINITY = JSNumber(Double.NEGATIVE_INFINITY)
    }
}
