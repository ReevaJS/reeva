package me.mattco.reeva.runtime.functions

import me.mattco.reeva.core.Agent.Companion.ifError
import me.mattco.reeva.core.Agent.Companion.throwError
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSNull
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.argument

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
        ifError { return INVALID_VALUE }
        return Operations.call(thisValue, arguments.argument(0), argList)
    }

    companion object {
        // Special object: do not initialize
        fun create(realm: Realm) = JSFunctionProto(realm)
    }
}
