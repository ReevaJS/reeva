package com.reevajs.reeva.runtime.promises

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue

class JSPromiseProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("constructor", realm.promiseCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineOwnProperty(Realm.`@@toStringTag`, "Promise".toValue(), Descriptor.CONFIGURABLE)

        defineBuiltin("catch", 1, ReevaBuiltin.PromiseProtoCatch)
        defineBuiltin("finally", 1, ReevaBuiltin.PromiseProtoFinally)
        defineBuiltin("then", 1, ReevaBuiltin.PromiseProtoThen)
    }

    companion object {
        fun create(realm: Realm) = JSPromiseProto(realm).initialize()

        @ECMAImpl("27.2.5.1")
        @JvmStatic
        fun catch(realm: Realm, arguments: JSArguments): JSValue {
            return Operations.invoke(
                realm,
                arguments.thisValue,
                "then".toValue(),
                listOf(JSUndefined, arguments.argument(0))
            )
        }

        @ECMAImpl("27.2.5.3")
        @JvmStatic
        fun finally(realm: Realm, arguments: JSArguments): JSValue {
            if (arguments.thisValue !is JSObject)
                Errors.IncompatibleMethodCall("Promise.prototype.finally").throwTypeError(realm)

            val onFinally = arguments.argument(0)
            val ctor = Operations.speciesConstructor(realm, arguments.thisValue, realm.promiseCtor)
            val (thenFinally, catchFinally) = if (!Operations.isCallable(onFinally)) {
                onFinally to onFinally
            } else {
                JSThenFinallyFunction.create(realm, ctor, onFinally) to JSCatchFinallyFunction.create(
                    realm,
                    ctor,
                    onFinally
                )
            }

            return Operations.invoke(realm, arguments.thisValue, "then".toValue(), listOf(thenFinally, catchFinally))
        }

        @ECMAImpl("27.2.5.4")
        @JvmStatic
        fun then(realm: Realm, arguments: JSArguments): JSValue {
            if (!Operations.isPromise(arguments.thisValue))
                Errors.IncompatibleMethodCall("Promise.prototype.then").throwTypeError(realm)

            val ctor = Operations.speciesConstructor(realm, arguments.thisValue, realm.promiseCtor)
            val resultCapability = Operations.newPromiseCapability(realm, ctor)
            return Operations.performPromiseThen(
                realm,
                arguments.thisValue,
                arguments.argument(0),
                arguments.argument(1),
                resultCapability
            )
        }
    }
}
