package me.mattco.reeva.runtime.functions

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSEmpty
import me.mattco.reeva.runtime.primitives.JSNull
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.argument
import me.mattco.reeva.utils.key
import me.mattco.reeva.utils.throwTypeError

class JSFunctionProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        val thrower = JSNativeFunction.fromLambda(realm, "", 0) { _, _ ->
            throwTypeError("cannot access \"caller\" or \"arguments\" properties on functions")
        }

        val desc = Descriptor(JSEmpty, Descriptor.CONFIGURABLE, thrower, thrower)
        defineOwnProperty("caller".key(), desc)
        defineOwnProperty("arguments".key(), desc)
    }

    @JSMethod("call", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun call(thisValue: JSValue, arguments: JSArguments): JSValue {
        if (!Operations.isCallable(thisValue))
            throwTypeError("TODO: message")
        return Operations.call(thisValue, arguments.argument(0), arguments.subList(1, arguments.size))
    }

    @JSMethod("apply", 2, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun apply(thisValue: JSValue, arguments: JSArguments): JSValue {
        if (!Operations.isCallable(thisValue))
            throwTypeError("TODO: message")
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
