package com.reevajs.reeva.runtime.other

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.key

class JSProxyCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Proxy", 2) {
    override fun init() {
        super.init()
        defineBuiltin("revocable", 2, ReevaBuiltin.ProxyCtorRevocable)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget == JSUndefined)
            Errors.CtorCallWithoutNew("Proxy").throwTypeError()
        return proxyCreate(arguments.argument(0), arguments.argument(1))
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSProxyCtor(realm).initialize()

        private fun proxyCreate(target: JSValue, handler: JSValue): JSObject {
            if (target !is JSObject)
                Errors.Proxy.CtorFirstArgType.throwTypeError()
            if (handler !is JSObject)
                Errors.Proxy.CtorSecondArgType.throwTypeError()
            return JSProxyObject.create(target, handler)
        }

        @ECMAImpl("28.2.2.1")
        @JvmStatic
        fun revocable(arguments: JSArguments): JSValue {
            val proxy = proxyCreate(arguments.argument(0), arguments.argument(1))

            val resultObj = JSObject.create()
            Operations.createDataPropertyOrThrow(resultObj, "proxy".key(), proxy)

            val revokeMethod = fromLambda("", 0) {
                (proxy as JSProxyObject).revoke()
                JSUndefined
            }
            Operations.createDataPropertyOrThrow(resultObj, "revoke".key(), revokeMethod)

            return resultObj
        }
    }
}
