package me.mattco.reeva.runtime.builtins

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.annotations.JSNativeAccessorGetter
import me.mattco.reeva.runtime.iterators.JSSetIterator
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSEmpty
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.*

class JSSetProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        // "The initial value of the 'keys' property is the same function object as the initial value
        // of the 'values' property"
        defineNativeFunction("keys".key(), 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE, ::values)
        // "The initial value of the @@iterator property is the same function object as the initial value
        // of the 'values' property"
        defineNativeFunction(Realm.`@@iterator`.key(), 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE, ::values)

        defineOwnProperty(Realm.`@@toStringTag`, "Set".toValue(), Descriptor.CONFIGURABLE)
    }

    @JSNativeAccessorGetter("size", "Ce")
    fun getSize(thisValue: JSValue): JSValue {
        val set = thisSetObject(thisValue, "getSize")
        return set.setData.size.toValue()
    }

    @JSMethod("add", 1)
    fun add(thisValue: JSValue, arguments: JSArguments): JSValue {
        val set = thisSetObject(thisValue, "add")
        set.setData.add(arguments.argument(0))
        set.insertionOrder.add(arguments.argument(0))
        return set
    }

    @JSMethod("clear", 0)
    fun clear(thisValue: JSValue, arguments: JSArguments): JSValue {
        val set = thisSetObject(thisValue, "clear")
        set.setData.clear()
        if (set.iterationCount == 0) {
            set.insertionOrder.clear()
        } else {
            set.insertionOrder.indices.forEach {
                set.insertionOrder[it] = JSEmpty
            }
        }
        return JSUndefined
    }

    @JSMethod("delete", 1)
    fun delete(thisValue: JSValue, arguments: JSArguments): JSValue {
        val set = thisSetObject(thisValue, "delete")
        val value = arguments.argument(0)
        if (set.iterationCount == 0) {
            set.insertionOrder.remove(value)
        } else {
            val index = set.insertionOrder.indexOf(value)
            if (index == -1)
                return false.toValue()
            set.insertionOrder[index] = JSEmpty
        }
        return set.setData.remove(value).toValue()
    }

    @JSMethod("entries", 0)
    fun entries(thisValue: JSValue, arguments: JSArguments): JSValue {
        val set = thisSetObject(thisValue, "entries")
        return JSSetIterator.create(realm, set, PropertyKind.KeyValue)
    }

    @JSMethod("forEach", 1)
    fun forEach(thisValue: JSValue, arguments: JSArguments): JSValue {
        val set = thisSetObject(thisValue, "forEach")
        val (callback, thisArg) = arguments.takeArgs(0..1)
        if (!Operations.isCallable(callback))
            Errors.Set.FirstArgNotCallable("forEach").throwTypeError()

        set.iterationCount++

        var index = 0
        while (index < set.insertionOrder.size) {
            val value = set.insertionOrder[index]
            if (value != JSEmpty)
                Operations.call(callback, thisArg, listOf(value, value, set))

            index++
        }

        set.iterationCount--

        return JSUndefined
    }

    @JSMethod("has", 1)
    fun has(thisValue: JSValue, arguments: JSArguments): JSValue {
        val set = thisSetObject(thisValue, "has")
        return (arguments.argument(0) in set.setData).toValue()
    }

    @JSMethod("values", 0)
    fun values(thisValue: JSValue, arguments: JSArguments): JSValue {
        val set = thisSetObject(thisValue, "values")
        return JSSetIterator.create(realm, set, PropertyKind.Value)
    }

    companion object {
        fun create(realm: Realm) = JSSetProto(realm).initialize()

        private fun thisSetObject(thisValue: JSValue, method: String): JSSetObject {
            if (thisValue !is JSSetObject)
                Errors.IncompatibleMethodCall("Set.prototype.$method").throwTypeError()
            return thisValue
        }
    }
}
