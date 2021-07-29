package com.reevajs.reeva.runtime.other

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.builtins.Builtin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.key

class JSProxyCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Proxy", 2) {
    override fun init() {
        super.init()
        defineBuiltin("revocable", 2, Builtin.ProxyCtorRevocable)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget == JSUndefined)
            Errors.CtorCallWithoutNew("Proxy").throwTypeError(realm)
        return proxyCreate(realm, arguments.argument(0), arguments.argument(1))
    }

    companion object {
        fun create(realm: Realm) = JSProxyCtor(realm).initialize()

        private fun proxyCreate(realm: Realm, target: JSValue, handler: JSValue): JSObject {
            if (target !is JSObject)
                Errors.Proxy.CtorFirstArgType.throwTypeError(realm)
            if (handler !is JSObject)
                Errors.Proxy.CtorSecondArgType.throwTypeError(realm)
            return JSProxyObject.create(realm, target, handler)
        }

        @ECMAImpl("28.2.2.1")
        @JvmStatic
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
    }
}
