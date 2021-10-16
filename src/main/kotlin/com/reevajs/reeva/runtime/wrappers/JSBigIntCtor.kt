package com.reevajs.reeva.runtime.wrappers

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
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

        defineBuiltin("asIntN", 2, ReevaBuiltin.BigIntCtorAsIntN)
        defineBuiltin("asUintN", 2, ReevaBuiltin.BigIntCtorAsUintN)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget != JSUndefined)
            Errors.BigInt.CtorCalledWithNew.throwTypeError(realm)
        val prim = Operations.toPrimitive(realm, arguments.argument(0), Operations.ToPrimitiveHint.AsNumber)
        if (prim is JSNumber) {
            if (!Operations.isIntegralNumber(prim))
                Errors.BigInt.Conversion(Operations.toPrintableString(prim)).throwRangeError(realm)
            return BigInteger.valueOf(prim.asLong).toValue()
        }
        return Operations.toBigInt(realm, prim)
    }

    companion object {
        fun create(realm: Realm) = JSBigIntCtor(realm).initialize()

        @ECMAImpl("21.2.2.1")
        @JvmStatic
        fun asIntN(realm: Realm, arguments: JSArguments): JSValue {
            val bits = Operations.toIndex(realm, arguments.argument(0))
            val bigint = Operations.toBigInt(realm, arguments.argument(1))
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
        fun asUintN(realm: Realm, arguments: JSArguments): JSValue {
            val bits = Operations.toIndex(realm, arguments.argument(0))
            val bigint = Operations.toBigInt(realm, arguments.argument(1))
            return bigint.number.mod(BigInteger.valueOf(2L).shiftLeft(bits - 1)).toValue()
        }
    }
}
