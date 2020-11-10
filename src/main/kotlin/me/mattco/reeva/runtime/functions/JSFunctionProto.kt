package me.mattco.reeva.runtime.functions

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.*
import me.mattco.reeva.utils.*
import kotlin.math.max

class JSFunctionProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        val thrower = JSNativeFunction.fromLambda(realm, "", 0) { _, _ ->
            Errors.Function.CallerArgumentsAccess.throwTypeError()
        }

        val desc = Descriptor(JSEmpty, Descriptor.CONFIGURABLE, thrower, thrower)
        defineOwnProperty("caller".key(), desc)
        defineOwnProperty("arguments".key(), desc)
    }

    @JSMethod("bind", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun bind(thisValue: JSValue, arguments: JSArguments): JSValue {
        if (!Operations.isCallable(thisValue))
            Errors.Function.BindNonFunction.throwTypeError()

        val args = if (arguments.size > 1) arguments.takeArgs(1 until arguments.size) else emptyList()
        val function = Operations.boundFunctionCreate(thisValue as JSFunction, arguments.argument(0), args)

        var length = JSNumber.ZERO

        if (Operations.hasOwnProperty(thisValue, "length".key())) {
            val targetLen = thisValue.get("length")
            if (targetLen is JSNumber) {
                if (targetLen.isPositiveInfinity) {
                    length = targetLen
                } else if (!targetLen.isNegativeInfinity) {
                    val targetLenAsInt = Operations.toIntegerOrInfinity(targetLen).let {
                        ecmaAssert(it.isFinite)
                        it.asInt
                    }
                    length = max(targetLenAsInt - args.size, 0).toValue()
                }
            }
        }

        Operations.definePropertyOrThrow(function, "length".key(), Descriptor(length, Descriptor.CONFIGURABLE))

        val targetName = thisValue.get("name").let {
            if (it !is JSString) "" else it.asString
        }

        Operations.setFunctionName(function, targetName.key(), "bound")
        return function
    }

    @JSMethod("call", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun call(thisValue: JSValue, arguments: JSArguments): JSValue {
        if (!Operations.isCallable(thisValue))
            Errors.Function.NonCallable("call").throwTypeError()
        return Operations.call(thisValue, arguments.argument(0), arguments.subList(1, arguments.size))
    }

    @JSMethod("apply", 2, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun apply(thisValue: JSValue, arguments: JSArguments): JSValue {
        if (!Operations.isCallable(thisValue))
            Errors.Function.NonCallable("apply").throwTypeError()
        val array = arguments.argument(1)
        if (array == JSUndefined || array == JSNull)
            return Operations.call(thisValue, arguments.argument(0))
        val argList = Operations.createListFromArrayLike(array)
        return Operations.call(thisValue, arguments.argument(0), argList)
    }

    companion object {
        // Special object: do not initialize
        fun create(realm: Realm) = JSFunctionProto(realm)
    }
}
