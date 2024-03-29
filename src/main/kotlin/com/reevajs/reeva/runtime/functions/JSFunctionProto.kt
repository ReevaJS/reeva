package com.reevajs.reeva.runtime.functions

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSNull
import com.reevajs.reeva.runtime.primitives.JSNumber
import com.reevajs.reeva.runtime.primitives.JSString
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.toIntegerOrInfinity
import com.reevajs.reeva.utils.*
import kotlin.math.max

class JSFunctionProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("name", "".toValue(), attrs { +conf; -enum; -writ })
        defineOwnProperty("length", 0.toValue(), attrs { +conf; -enum; -writ })

        defineBuiltin("apply", 2, ::apply)
        defineBuiltin("bind", 1, ::bind)
        defineBuiltin("call", 1, ::call)
    }

    companion object {
        // Special object: do not initialize
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSFunctionProto(realm)

        @ECMAImpl("20.2.3.1")
        @JvmStatic
        fun apply(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (!AOs.isCallable(thisValue))
                Errors.Function.NonCallable("apply").throwTypeError()
            val array = arguments.argument(1)
            if (array == JSUndefined || array == JSNull)
                return AOs.call(thisValue, arguments.argument(0))
            val argList = AOs.createListFromArrayLike(array)
            return AOs.call(thisValue, arguments.argument(0), argList)
        }

        @ECMAImpl("20.2.3.2")
        @JvmStatic
        fun bind(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (!AOs.isCallable(thisValue))
                Errors.Function.BindNonFunction.throwTypeError()

            val args = if (arguments.size > 1) arguments.takeArgs(1 until arguments.size) else emptyList()
            val function = AOs.boundFunctionCreate(thisValue, JSArguments(args, arguments.argument(0)))

            var length = JSNumber.ZERO

            if (AOs.hasOwnProperty(thisValue, "length".key())) {
                val targetLen = thisValue.get("length")
                if (targetLen is JSNumber) {
                    if (targetLen.isPositiveInfinity) {
                        length = targetLen
                    } else if (!targetLen.isNegativeInfinity) {
                        val targetLenAsInt = targetLen.toIntegerOrInfinity().let {
                            ecmaAssert(it.isFinite)
                            it.asInt
                        }
                        length = max(targetLenAsInt - args.size, 0).toValue()
                    }
                }
            }

            AOs.definePropertyOrThrow(
                function,
                "length".key(),
                Descriptor(length, Descriptor.CONFIGURABLE)
            )

            val targetName = thisValue.get("name").let {
                if (it !is JSString) "" else it.asString
            }

            AOs.setFunctionName(function, targetName.key(), "bound")
            return function
        }

        @ECMAImpl("20.2.3.3")
        @JvmStatic
        fun call(arguments: JSArguments): JSValue {
            if (!AOs.isCallable(arguments.thisValue))
                Errors.Function.NonCallable("call").throwTypeError()
            return AOs.call(
                arguments.thisValue,
                arguments.argument(0),
                arguments.subList(1, arguments.size)
            )
        }
    }
}
