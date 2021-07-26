package com.reevajs.reeva.runtime.primitives

import com.reevajs.reeva.runtime.JSValue
import java.math.BigInteger

class JSBigInt(val number: BigInteger) : JSValue() {
    companion object {
        @JvmStatic
        val ZERO = JSBigInt(BigInteger.ZERO)
        @JvmStatic
        val ONE = JSBigInt(BigInteger.ONE)
    }
}
