package me.mattco.reeva.runtime.values.functions

import me.mattco.reeva.runtime.Agent.Companion.checkError
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.values.objects.Descriptor
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.primitives.JSNull
import me.mattco.reeva.runtime.values.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.argument
import me.mattco.reeva.utils.throwError

class JSFunctionProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    @JSMethod("call", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun call(thisValue: JSValue, arguments: JSArguments): JSValue {
        if (!Operations.isCallable(thisValue)) {
            throwError<JSTypeErrorObject>("TODO: message")
            return INVALID_VALUE
        }
        return Operations.call(thisValue, arguments.argument(0), arguments.subList(1, arguments.size))
    }

    @JSMethod("apply", 2, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun apply(thisValue: JSValue, arguments: JSArguments): JSValue {
        if (!Operations.isCallable(thisValue)) {
            throwError<JSTypeErrorObject>("TODO: message")
            return INVALID_VALUE
        }
        val array = arguments.argument(1)
        if (array == JSUndefined || array == JSNull)
            return Operations.call(thisValue, arguments.argument(0))
        val argList = Operations.createListFromArrayLike(array)
        checkError() ?: return INVALID_VALUE
        return Operations.call(thisValue, arguments.argument(0), argList)
    }

    companion object {
        // Special object: do not initialize
        fun create(realm: Realm) = JSFunctionProto(realm)
    }
}
