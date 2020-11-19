package me.mattco.reeva.runtime.primitives

import me.mattco.reeva.runtime.JSValue
import java.math.BigInteger

class JSBigInt(val number: BigInteger) : JSValue() {
    companion object {
        val ZERO = JSBigInt(BigInteger.ZERO)
        val ONE = JSBigInt(BigInteger.ONE)
    }
}
