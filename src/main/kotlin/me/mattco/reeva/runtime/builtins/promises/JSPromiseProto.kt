package me.mattco.reeva.runtime.builtins.promises

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.*

class JSPromiseProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("constructor", realm.promiseCtor, 0)
        defineOwnProperty(Realm.`@@toStringTag`, "Promise".toValue(), Descriptor.CONFIGURABLE)
    }

    @JSMethod("catch", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun catch(thisValue: JSValue, arguments: JSArguments): JSValue {
        return Operations.invoke(thisValue, "then".toValue(), listOf(JSUndefined, arguments.argument(0)))
    }

    @JSMethod("finally", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun finally(thisValue: JSValue, arguments: JSArguments): JSValue {
        if (thisValue !is JSObject)
            throwTypeError("Promise.prototype.finally called on incompatible value")

        val onFinally = arguments.argument(0)
        val ctor = Operations.speciesConstructor(thisValue, realm.promiseCtor)
        val (thenFinally, catchFinally) = if (!Operations.isCallable(onFinally)) {
            onFinally to onFinally
        } else {
            JSThenFinallyFunction.create(realm, ctor, onFinally as JSFunction) to JSCatchFinallyFunction.create(realm, ctor, onFinally)
        }

        return Operations.invoke(thisValue, "then".toValue(), listOf(thenFinally, catchFinally))
    }

    @JSMethod("then", 2, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun then(thisValue: JSValue, arguments: JSArguments): JSValue {
        if (thisValue !is JSPromiseObject)
            throwTypeError("Promise.prototype.then calls on an incompatible value")

        val ctor = Operations.speciesConstructor(thisValue, realm.promiseCtor)
        val resultCapability = Operations.newPromiseCapability(ctor)
        return Operations.performPromiseThen(thisValue, arguments.argument(0), arguments.argument(1), resultCapability)
    }

    companion object {
        fun create(realm: Realm) = JSPromiseProto(realm).also { it.init() }
    }
}
