package me.mattco.reeva.runtime.builtins.promises

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.toValue

class JSPromiseProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("constructor", realm.promiseCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineOwnProperty(Realm.`@@toStringTag`, "Promise".toValue(), Descriptor.CONFIGURABLE)

        defineNativeFunction("catch", 1, ::catch)
        defineNativeFunction("finally", 1, ::finally)
        defineNativeFunction("then", 1, ::then)
    }

    fun catch(realm: Realm, arguments: JSArguments): JSValue {
        return Operations.invoke(realm, arguments.thisValue, "then".toValue(), listOf(JSUndefined, arguments.argument(0)))
    }

    fun finally(realm: Realm, arguments: JSArguments): JSValue {
        if (arguments.thisValue !is JSObject)
            Errors.IncompatibleMethodCall("Promise.prototype.finally").throwTypeError(realm)

        val onFinally = arguments.argument(0)
        val ctor = Operations.speciesConstructor(realm, arguments.thisValue, realm.promiseCtor)
        val (thenFinally, catchFinally) = if (!Operations.isCallable(onFinally)) {
            onFinally to onFinally
        } else {
            JSThenFinallyFunction.create(realm, ctor, onFinally) to JSCatchFinallyFunction.create(realm, ctor, onFinally)
        }

        return Operations.invoke(realm, arguments.thisValue, "then".toValue(), listOf(thenFinally, catchFinally))
    }

    fun then(realm: Realm, arguments: JSArguments): JSValue {
        if (!Operations.isPromise(arguments.thisValue))
            Errors.IncompatibleMethodCall("Promise.prototype.then").throwTypeError(realm)

        val ctor = Operations.speciesConstructor(realm, arguments.thisValue, realm.promiseCtor)
        val resultCapability = Operations.newPromiseCapability(realm, ctor)
        return Operations.performPromiseThen(realm, arguments.thisValue, arguments.argument(0), arguments.argument(1), resultCapability)
    }

    companion object {
        fun create(realm: Realm) = JSPromiseProto(realm).initialize()
    }
}
