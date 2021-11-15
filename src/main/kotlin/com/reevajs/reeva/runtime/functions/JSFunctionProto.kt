package com.reevajs.reeva.runtime.functions

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.*
import com.reevajs.reeva.runtime.toIntegerOrInfinity
import com.reevajs.reeva.utils.*
import kotlin.math.max

class JSFunctionProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        val thrower = JSRunnableFunction.create("", 0) {
            Errors.Function.CallerArgumentsAccess.throwTypeError()
        }

        defineOwnProperty("name", "".toValue(), attrs { +conf; -enum; -writ })
        defineOwnProperty("length", 0.toValue(), attrs { +conf; -enum; -writ })

        val desc = Descriptor(JSAccessor(thrower, thrower), Descriptor.CONFIGURABLE)
        defineOwnProperty("caller".key(), desc)
        defineOwnProperty("arguments".key(), desc)

        defineBuiltin("apply", 2, ReevaBuiltin.FunctionProtoApply)
        defineBuiltin("bind", 1, ReevaBuiltin.FunctionProtoBind)
        defineBuiltin("call", 1, ReevaBuiltin.FunctionProtoCall)
    }

    companion object {
        // Special object: do not initialize
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSFunctionProto(realm)

        @ECMAImpl("20.2.3.1")
        @JvmStatic
        fun apply(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (!Operations.isCallable(thisValue))
                Errors.Function.NonCallable("apply").throwTypeError()
            val array = arguments.argument(1)
            if (array == JSUndefined || array == JSNull)
                return Operations.call(thisValue, arguments.argument(0))
            val argList = Operations.createListFromArrayLike(array)
            return Operations.call(thisValue, arguments.argument(0), argList)
        }

        @ECMAImpl("20.2.3.2")
        @JvmStatic
        fun bind(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (!Operations.isCallable(thisValue))
                Errors.Function.BindNonFunction.throwTypeError()

            val args = if (arguments.size > 1) arguments.takeArgs(1 until arguments.size) else emptyList()
            val function = Operations.boundFunctionCreate(thisValue, JSArguments(args, arguments.argument(0)))

            var length = JSNumber.ZERO

            if (Operations.hasOwnProperty(thisValue, "length".key())) {
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

            Operations.definePropertyOrThrow(
                function,
                "length".key(),
                Descriptor(length, Descriptor.CONFIGURABLE)
            )

            val targetName = thisValue.get("name").let {
                if (it !is JSString) "" else it.asString
            }

            Operations.setFunctionName(function, targetName.key(), "bound")
            return function
        }

        @ECMAImpl("20.2.3.3")
        @JvmStatic
        fun call(arguments: JSArguments): JSValue {
            if (!Operations.isCallable(arguments.thisValue))
                Errors.Function.NonCallable("call").throwTypeError()
            return Operations.call(
                arguments.thisValue,
                arguments.argument(0),
                arguments.subList(1, arguments.size)
            )
        }
    }
}
