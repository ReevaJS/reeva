package me.mattco.reeva.runtime.builtins

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.argument
import me.mattco.reeva.utils.key
import me.mattco.reeva.utils.throwTypeError

class JSProxyCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Proxy", 2) {
    init {
        isConstructable = true
    }

    override fun call(thisValue: JSValue, arguments: JSArguments): JSValue {
        throwTypeError("Proxy must be called with the 'new' keyword")
    }

    override fun construct(arguments: JSArguments, newTarget: JSValue): JSValue {
        return proxyCreate(realm, arguments.argument(0), arguments.argument(1))
    }

    @JSMethod("revocable", 2, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun revocable(thisValue: JSValue, arguments: JSArguments): JSValue {
        val proxy = proxyCreate(realm, arguments.argument(0), arguments.argument(1))

        val resultObj = JSObject.create(realm)
        Operations.createDataPropertyOrThrow(resultObj, "proxy".key(), proxy)

        val revokeMethod = fromLambda(realm, "", 0) { _, _ ->
            (proxy as JSProxyObject).revoke()
            JSUndefined
        }
        Operations.createDataPropertyOrThrow(resultObj, "revoke".key(), revokeMethod)

        return resultObj
    }

    companion object {
        fun create(realm: Realm) = JSProxyCtor(realm).also { it.init() }

        private fun proxyCreate(realm: Realm, target: JSValue, handler: JSValue): JSObject {
            if (target !is JSObject)
                throwTypeError("the first argument to the Proxy constructor must be an object")
            if (handler !is JSObject)
                throwTypeError("the second argument to the Proxy constructor must be an object")
            return JSProxyObject.create(realm, target, handler)
        }
    }
}
