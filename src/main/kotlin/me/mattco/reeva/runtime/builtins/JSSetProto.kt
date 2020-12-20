package me.mattco.reeva.runtime.builtins

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.iterators.JSSetIterator
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSEmpty
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.*

class JSSetProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineNativeAccessor("size", attrs { +conf -enum }, ::getSize, null)
        defineOwnProperty(Realm.`@@toStringTag`, "Set".toValue(), Descriptor.CONFIGURABLE)
        defineNativeFunction("add", 1, ::add)
        defineNativeFunction("clear", 1, ::clear)
        defineNativeFunction("delete", 1, ::delete)
        defineNativeFunction("entries", 1, ::entries)
        defineNativeFunction("forEach", 1, ::forEach)
        defineNativeFunction("has", 1, ::has)
        defineNativeFunction("values", 1, ::values)

        // "The initial value of the 'keys' property is the same function object as the initial value
        // of the 'values' property"
        defineNativeFunction("keys", 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE, ::values)
        // "The initial value of the @@iterator property is the same function object as the initial value
        // of the 'values' property"
        defineNativeFunction(Realm.`@@iterator`.key(), 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE, ::values)
    }

    fun getSize(thisValue: JSValue): JSValue {
        val set = thisSetObject(thisValue, "getSize")
        return set.setData.size.toValue()
    }

    fun add(thisValue: JSValue, arguments: JSArguments): JSValue {
        val set = thisSetObject(thisValue, "add")
        set.setData.add(arguments.argument(0))
        set.insertionOrder.add(arguments.argument(0))
        return set
    }

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

    fun entries(thisValue: JSValue, arguments: JSArguments): JSValue {
        val set = thisSetObject(thisValue, "entries")
        return JSSetIterator.create(realm, set, PropertyKind.KeyValue)
    }

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

    fun has(thisValue: JSValue, arguments: JSArguments): JSValue {
        val set = thisSetObject(thisValue, "has")
        return (arguments.argument(0) in set.setData).toValue()
    }

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
