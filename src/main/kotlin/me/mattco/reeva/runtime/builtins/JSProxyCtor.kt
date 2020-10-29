package me.mattco.reeva.runtime.builtins

import me.mattco.reeva.core.Agent.Companion.ifError
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.argument
import me.mattco.reeva.utils.key
import me.mattco.reeva.utils.throwError

class JSProxyCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Proxy", 2) {
    init {
        isConstructable = true
    }

    override fun call(thisValue: JSValue, arguments: JSArguments): JSValue {
        throwError<JSTypeErrorObject>("Proxy must be called with the 'new' keyword")
        return INVALID_VALUE
    }

    override fun construct(arguments: JSArguments, newTarget: JSValue): JSValue {
        return proxyCreate(realm, arguments.argument(0), arguments.argument(1))
    }

    @JSMethod("revocable", 2, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun revocable(thisValue: JSValue, arguments: JSArguments): JSValue {
        val proxy = proxyCreate(realm, arguments.argument(0), arguments.argument(1))
        ifError { return INVALID_VALUE }

        val resultObj = JSObject.create(realm)
        Operations.createDataPropertyOrThrow(resultObj, "proxy".key(), proxy)
        ifError { return INVALID_VALUE }

        val revokeMethod = fromLambda(realm, "", 0) { _, _ ->
            (proxy as JSProxyObject).revoke()
            JSUndefined
        }
        Operations.createDataPropertyOrThrow(resultObj, "revoke".key(), revokeMethod)
        ifError { return INVALID_VALUE }

        return resultObj
    }

    companion object {
        fun create(realm: Realm) = JSProxyCtor(realm).also { it.init() }

        private fun proxyCreate(realm: Realm, target: JSValue, handler: JSValue): JSObject {
            if (target !is JSObject) {
                throwError<JSTypeErrorObject>("the first argument to the Proxy constructor must be an object")
                return INVALID_OBJECT
            }
            if (handler !is JSObject) {
                throwError<JSTypeErrorObject>("the second argument to the Proxy constructor must be an object")
                return INVALID_OBJECT
            }
            return JSProxyObject.create(realm, target, handler)
        }
    }
}
