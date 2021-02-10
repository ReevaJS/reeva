package me.mattco.reeva.runtime.builtins.promises

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.*

class JSPromiseProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("constructor", realm.promiseCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineOwnProperty(Realm.`@@toStringTag`, "Promise".toValue(), Descriptor.CONFIGURABLE)

        defineNativeFunction("catch", 1, ::catch)
        defineNativeFunction("finally", 1, ::finally)
        defineNativeFunction("then", 1, ::then)
    }

    fun catch(arguments: JSArguments): JSValue {
        return Operations.invoke(arguments.thisValue, "then".toValue(), listOf(JSUndefined, arguments.argument(0)))
    }

    fun finally(arguments: JSArguments): JSValue {
        if (arguments.thisValue !is JSObject)
            Errors.IncompatibleMethodCall("Promise.prototype.finally").throwTypeError()

        val onFinally = arguments.argument(0)
        val ctor = Operations.speciesConstructor(arguments.thisValue, realm.promiseCtor)
        val (thenFinally, catchFinally) = if (!Operations.isCallable(onFinally)) {
            onFinally to onFinally
        } else {
            JSThenFinallyFunction.create(realm, ctor, onFinally as JSFunction) to JSCatchFinallyFunction.create(realm, ctor, onFinally)
        }

        return Operations.invoke(arguments.thisValue, "then".toValue(), listOf(thenFinally, catchFinally))
    }

    fun then(arguments: JSArguments): JSValue {
        if (!Operations.isPromise(arguments.thisValue))
            Errors.IncompatibleMethodCall("Promise.prototype.then").throwTypeError()

        val ctor = Operations.speciesConstructor(arguments.thisValue, realm.promiseCtor)
        val resultCapability = Operations.newPromiseCapability(ctor)
        return Operations.performPromiseThen(arguments.thisValue, arguments.argument(0), arguments.argument(1), resultCapability)
    }

    companion object {
        fun create(realm: Realm) = JSPromiseProto(realm).initialize()
    }
}
