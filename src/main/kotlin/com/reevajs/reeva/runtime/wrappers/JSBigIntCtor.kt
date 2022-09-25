package com.reevajs.reeva.runtime.wrappers

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.primitives.JSBigInt
import com.reevajs.reeva.runtime.primitives.JSNumber
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue
import java.math.BigInteger

class JSBigIntCtor private constructor(realm: Realm) : JSNativeFunction(realm, "BigInt", 1) {
    override fun init() {
        super.init()

        defineBuiltin("asIntN", 2, ::asIntN)
        defineBuiltin("asUintN", 2, ::asUintN)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget != JSUndefined)
            Errors.BigInt.CtorCalledWithNew.throwTypeError()
        val prim = arguments.argument(0).toPrimitive(AOs.ToPrimitiveHint.AsNumber)
        if (prim is JSNumber) {
            if (!AOs.isIntegralNumber(prim))
                Errors.BigInt.Conversion(prim.toString()).throwRangeError()
            return BigInteger.valueOf(prim.asLong).toValue()
        }
        return prim.toBigInt()
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSBigIntCtor(realm).initialize()

        @ECMAImpl("21.2.2.1")
        @JvmStatic
        fun asIntN(arguments: JSArguments): JSValue {
            val bits = arguments.argument(0).toIndex()
            val bigint = arguments.argument(1).toBigInt()
            if (bits == 0)
                return JSBigInt.ZERO
            val modRhs = BigInteger.valueOf(2L).shiftLeft(bits - 1)
            val mod = bigint.number.mod(modRhs)
            if (mod >= modRhs.divide(BigInteger.valueOf(2)))
                return (mod - modRhs).toValue()
            return mod.toValue()
        }

        @ECMAImpl("21.2.2.2")
        @JvmStatic
        fun asUintN(arguments: JSArguments): JSValue {
            val bits = arguments.argument(0).toIndex()
            val bigint = arguments.argument(1).toBigInt()
            return bigint.number.mod(BigInteger.valueOf(2L).shiftLeft(bits - 1)).toValue()
        }
    }
}
