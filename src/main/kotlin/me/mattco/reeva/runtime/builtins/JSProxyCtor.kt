package me.mattco.reeva.runtime.builtins

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.key

class JSProxyCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Proxy", 2) {
    override fun init() {
        super.init()
        defineNativeFunction("revocable", 2, function = ::revocable)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget == JSUndefined)
            Errors.CtorCallWithoutNew("Proxy").throwTypeError(realm)
        return proxyCreate(realm, arguments.argument(0), arguments.argument(1))
    }

    fun revocable(realm: Realm, arguments: JSArguments): JSValue {
        val proxy = proxyCreate(realm, arguments.argument(0), arguments.argument(1))

        val resultObj = JSObject.create(realm)
        Operations.createDataPropertyOrThrow(realm, resultObj, "proxy".key(), proxy)

        val revokeMethod = fromLambda(realm, "", 0) { _, _ ->
            (proxy as JSProxyObject).revoke()
            JSUndefined
        }
        Operations.createDataPropertyOrThrow(realm, resultObj, "revoke".key(), revokeMethod)

        return resultObj
    }

    companion object {
        private fun proxyCreate(realm: Realm, target: JSValue, handler: JSValue): JSObject {
            if (target !is JSObject)
                Errors.Proxy.CtorFirstArgType.throwTypeError(realm)
            if (handler !is JSObject)
                Errors.Proxy.CtorSecondArgType.throwTypeError(realm)
            return JSProxyObject.create(realm, target, handler)
        }

        fun create(realm: Realm) = JSProxyCtor(realm).initialize()
    }
}
