package me.mattco.reeva.runtime.functions

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.*
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.ecmaAssert
import me.mattco.reeva.utils.key
import me.mattco.reeva.utils.toValue
import kotlin.math.max

class JSFunctionProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        val thrower = JSNativeFunction.fromLambda(realm, "", 0) { realm, _ ->
            Errors.Function.CallerArgumentsAccess.throwTypeError(realm)
        }

        val desc = Descriptor(JSAccessor(thrower, thrower), Descriptor.CONFIGURABLE)
        defineOwnProperty("caller".key(), desc)
        defineOwnProperty("arguments".key(), desc)

        defineNativeFunction("bind", 1, ::bind)
        defineNativeFunction("call", 1, ::call)
        defineNativeFunction("apply", 2, ::apply)
    }

    private fun bind(realm: Realm, arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        if (!Operations.isCallable(thisValue))
            Errors.Function.BindNonFunction.throwTypeError(realm)

        val args = if (arguments.size > 1) arguments.takeArgs(1 until arguments.size) else emptyList()
        val function = Operations.boundFunctionCreate(realm, thisValue, JSArguments(args, arguments.argument(0)))

        var length = JSNumber.ZERO

        if (Operations.hasOwnProperty(thisValue, "length".key())) {
            val targetLen = thisValue.get("length")
            if (targetLen is JSNumber) {
                if (targetLen.isPositiveInfinity) {
                    length = targetLen
                } else if (!targetLen.isNegativeInfinity) {
                    val targetLenAsInt = Operations.toIntegerOrInfinity(realm, targetLen).let {
                        ecmaAssert(it.isFinite)
                        it.asInt
                    }
                    length = max(targetLenAsInt - args.size, 0).toValue()
                }
            }
        }

        Operations.definePropertyOrThrow(realm, function, "length".key(), Descriptor(length, Descriptor.CONFIGURABLE))

        val targetName = thisValue.get("name").let {
            if (it !is JSString) "" else it.asString
        }

        Operations.setFunctionName(realm, function, targetName.key(), "bound")
        return function
    }

    private fun call(realm: Realm, arguments: JSArguments): JSValue {
        if (!Operations.isCallable(arguments.thisValue))
            Errors.Function.NonCallable("call").throwTypeError(realm)
        return Operations.call(realm, arguments.thisValue, arguments.argument(0), arguments.subList(1, arguments.size))
    }

    private fun apply(realm: Realm, arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        if (!Operations.isCallable(thisValue))
            Errors.Function.NonCallable("apply").throwTypeError(realm)
        val array = arguments.argument(1)
        if (array == JSUndefined || array == JSNull)
            return Operations.call(realm, thisValue, arguments.argument(0))
        val argList = Operations.createListFromArrayLike(realm, array)
        return Operations.call(realm, thisValue, arguments.argument(0), argList)
    }

    companion object {
        // Special object: do not initialize
        fun create(realm: Realm) = JSFunctionProto(realm)
    }
}
