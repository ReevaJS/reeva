package com.reevajs.reeva.runtime.builtins

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSArguments
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.SlotName
import com.reevajs.reeva.runtime.iterators.JSSetIterator
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSEmpty
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.attrs
import com.reevajs.reeva.utils.key
import com.reevajs.reeva.utils.toValue

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
        defineNativeFunction("keys", 0, ::values)
        // "The initial value of the @@iterator property is the same function object as the initial value
        // of the 'values' property"
        defineNativeFunction(Realm.`@@iterator`.key(), 0, function = ::values)
    }

    fun getSize(realm: Realm, thisValue: JSValue): JSValue {
        return thisSetObject(realm, thisValue, "getSize").set.size.toValue()
    }

    fun add(realm: Realm, arguments: JSArguments): JSValue {
        val data = thisSetObject(realm, arguments.thisValue, "add")
        data.set.add(arguments.argument(0))
        data.insertionOrder.add(arguments.argument(0))
        return arguments.thisValue
    }

    fun clear(realm: Realm, arguments: JSArguments): JSValue {
        val data = thisSetObject(realm, arguments.thisValue, "clear")
        data.set.clear()
        if (data.iterationCount == 0) {
            data.insertionOrder.clear()
        } else {
            data.insertionOrder.indices.forEach {
                data.insertionOrder[it] = JSEmpty
            }
        }
        return JSUndefined
    }

    fun delete(realm: Realm, arguments: JSArguments): JSValue {
        val data = thisSetObject(realm, arguments.thisValue, "delete")
        val value = arguments.argument(0)
        if (data.iterationCount == 0) {
            data.insertionOrder.remove(value)
        } else {
            val index = data.insertionOrder.indexOf(value)
            if (index == -1)
                return false.toValue()
            data.insertionOrder[index] = JSEmpty
        }
        return data.set.remove(value).toValue()
    }

    fun entries(realm: Realm, arguments: JSArguments): JSValue {
        val data = thisSetObject(realm, arguments.thisValue, "entries")
        return JSSetIterator.create(realm, data, PropertyKind.KeyValue)
    }

    fun forEach(realm: Realm, arguments: JSArguments): JSValue {
        val data = thisSetObject(realm, arguments.thisValue, "forEach")
        val (callback, thisArg) = arguments.takeArgs(0..1)
        if (!Operations.isCallable(callback))
            Errors.Set.FirstArgNotCallable("forEach").throwTypeError(realm)

        data.iterationCount++

        var index = 0
        while (index < data.insertionOrder.size) {
            val value = data.insertionOrder[index]
            if (value != JSEmpty)
                Operations.call(realm, callback, thisArg, listOf(value, value, arguments.thisValue))

            index++
        }

        data.iterationCount--

        return JSUndefined
    }

    fun has(realm: Realm, arguments: JSArguments): JSValue {
        val data = thisSetObject(realm, arguments.thisValue, "has")
        return (arguments.argument(0) in data.set).toValue()
    }

    fun values(realm: Realm, arguments: JSArguments): JSValue {
        val data = thisSetObject(realm, arguments.thisValue, "values")
        return JSSetIterator.create(realm, data, PropertyKind.Value)
    }

    companion object {
        fun create(realm: Realm) = JSSetProto(realm).initialize()

        private fun thisSetObject(realm: Realm, thisValue: JSValue, method: String): JSSetObject.SetData {
            if (!Operations.requireInternalSlot(thisValue, SlotName.SetData))
                Errors.IncompatibleMethodCall("Set.prototype.$method").throwTypeError(realm)
            return thisValue.getSlotAs(SlotName.SetData)
        }
    }
}
