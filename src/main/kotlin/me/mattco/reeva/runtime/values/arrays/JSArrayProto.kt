package me.mattco.reeva.runtime.values.arrays

import me.mattco.reeva.runtime.Agent.Companion.checkError
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.contexts.ExecutionContext
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.values.objects.Descriptor
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.argument
import me.mattco.reeva.utils.throwError
import me.mattco.reeva.utils.toValue

class JSArrayProto private constructor(realm: Realm) : JSArrayObject(realm, null) {
    override fun init() {
        // No super call to avoid prototype complications

        internalSetPrototype(realm.objectProto)
        defineOwnProperty("prototype", realm.objectProto, 0)
        configureInstanceProperties()

        defineOwnProperty("constructor", realm.arrayCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    @JSMethod("forEach", 1)
    fun forEach(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(thisValue)
        checkError() ?: return INVALID_VALUE
        val len = Operations.lengthOfArrayLike(obj)
        checkError() ?: return INVALID_VALUE
        val callbackFn = arguments.argument(0)
        if (!Operations.isCallable(callbackFn)) {
            throwError<JSTypeErrorObject>("the first argument to Array.prototype.forEach must be callable")
            return INVALID_VALUE
        }
        for (index in obj.indexedProperties.indices()) {
            if (index >= len)
                break
            val value = obj.indexedProperties.get(thisValue, index)
            checkError() ?: return INVALID_VALUE
            Operations.call(callbackFn, arguments.argument(1), listOf(value, index.toValue(), obj))
        }

        return JSUndefined
    }

    companion object {
        fun create(realm: Realm) = JSArrayProto(realm).also { it.init() }
    }
}
