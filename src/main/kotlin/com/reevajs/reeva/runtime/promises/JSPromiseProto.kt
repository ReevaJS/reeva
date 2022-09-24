package com.reevajs.reeva.runtime.promises

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue

class JSPromiseProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        val realm = Agent.activeAgent.getActiveRealm()
        defineOwnProperty("constructor", realm.promiseCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "Promise".toValue(), Descriptor.CONFIGURABLE)

        defineBuiltin("catch", 1, ::catch)
        defineBuiltin("finally", 1, ::finally)
        defineBuiltin("then", 1, ::then)
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSPromiseProto(realm).initialize()

        @ECMAImpl("27.2.5.1")
        @JvmStatic
        fun catch(arguments: JSArguments): JSValue {
            return Operations.invoke(
                arguments.thisValue,
                "then".toValue(),
                listOf(JSUndefined, arguments.argument(0))
            )
        }

        @ECMAImpl("27.2.5.3")
        @JvmStatic
        fun finally(arguments: JSArguments): JSValue {
            if (arguments.thisValue !is JSObject)
                Errors.IncompatibleMethodCall("Promise.prototype.finally").throwTypeError()

            val onFinally = arguments.argument(0)
            val ctor = Operations.speciesConstructor(
                arguments.thisValue,
                Agent.activeAgent.getActiveRealm().promiseCtor,
            )
            val (thenFinally, catchFinally) = if (!Operations.isCallable(onFinally)) {
                onFinally to onFinally
            } else {
                JSThenFinallyFunction.create(ctor, onFinally) to JSCatchFinallyFunction.create(
                    ctor,
                    onFinally
                )
            }

            return Operations.invoke(arguments.thisValue, "then".toValue(), listOf(thenFinally, catchFinally))
        }

        @ECMAImpl("27.2.5.4")
        @JvmStatic
        fun then(arguments: JSArguments): JSValue {
            if (!Operations.isPromise(arguments.thisValue))
                Errors.IncompatibleMethodCall("Promise.prototype.then").throwTypeError()

            val ctor = Operations.speciesConstructor(
                arguments.thisValue,
                Agent.activeAgent.getActiveRealm().promiseCtor,
            )
            val resultCapability = Operations.newPromiseCapability(ctor)
            return Operations.performPromiseThen(
                arguments.thisValue,
                arguments.argument(0),
                arguments.argument(1),
                resultCapability
            )
        }
    }
}
