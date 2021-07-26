package com.reevajs.reeva.runtime.builtins

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSArguments
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.SlotName
import com.reevajs.reeva.runtime.iterators.JSMapIterator
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSEmpty
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.attrs
import com.reevajs.reeva.utils.key
import com.reevajs.reeva.utils.toValue

class JSMapProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty(Realm.`@@toStringTag`, "Map".toValue(), Descriptor.CONFIGURABLE)
        defineNativeAccessor("size", attrs { +conf -enum }, ::getSize, null)
        defineNativeFunction("clear", 0, ::clear)
        defineNativeFunction("delete", 1, ::delete)
        defineNativeFunction("entries", 0, ::entries)
        defineNativeFunction("forEach", 1, ::forEach)
        defineNativeFunction("get", 1, ::get)
        defineNativeFunction("has", 1, ::has)
        defineNativeFunction("keys", 1, ::keys)
        defineNativeFunction("set", 2, ::set)
        defineNativeFunction("values", 2, ::values)
        // "The initial value of the @@iterator property is the same function object
        // as the initial value of the 'entries' property."
        defineNativeFunction(Realm.`@@iterator`.key(), 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE, function = ::entries)
    }

    fun getSize(realm: Realm, thisValue: JSValue): JSValue {
        return thisMapData(realm, thisValue, "size").map.size.toValue()
    }

    fun clear(realm: Realm, arguments: JSArguments): JSValue {
        val data = thisMapData(realm, arguments.thisValue, "clear")
        data.map.clear()
        if (data.iterationCount == 0) {
            data.keyInsertionOrder.clear()
        } else {
            data.keyInsertionOrder.indices.forEach {
                data.keyInsertionOrder[it] = JSEmpty
            }
        }
        return JSUndefined
    }

    fun delete(realm: Realm, arguments: JSArguments): JSValue {
        val data = thisMapData(realm, arguments.thisValue, "delete")
        val key = arguments.argument(0)
        if (data.iterationCount == 0) {
            data.keyInsertionOrder.remove(key)
        } else {
            val index = data.keyInsertionOrder.indexOf(key)
            if (index == -1)
                return false.toValue()
            data.keyInsertionOrder[index] = JSEmpty
        }

        return (data.map.remove(key) != null).toValue()
    }

    fun entries(realm: Realm, arguments: JSArguments): JSValue {
        val data = thisMapData(realm, arguments.thisValue, "entries")
        return JSMapIterator.create(realm, data, PropertyKind.KeyValue)
    }

    fun forEach(realm: Realm, arguments: JSArguments): JSValue {
        val data = thisMapData(realm, arguments.thisValue, "forEach")
        val (callback, thisArg) = arguments.takeArgs(0..1)
        if (!Operations.isCallable(callback))
            Errors.Map.CallableFirstArg("forEach")

        data.iterationCount++

        var index = 0
        while (index < data.keyInsertionOrder.size) {
            val key = data.keyInsertionOrder[index]
            if (key != JSEmpty)
                Operations.call(realm, callback, thisArg, listOf(data.map[key]!!, key, arguments.thisValue))

            index++
        }

        data.iterationCount--

        return JSUndefined
    }

    fun get(realm: Realm, arguments: JSArguments): JSValue {
        val data = thisMapData(realm, arguments.thisValue, "get")
        return data.map[arguments.argument(0)] ?: JSUndefined
    }

    fun has(realm: Realm, arguments: JSArguments): JSValue {
        val data = thisMapData(realm, arguments.thisValue, "has")
        return (arguments.argument(0) in data.map).toValue()
    }

    fun keys(realm: Realm, arguments: JSArguments): JSValue {
        val map = thisMapData(realm, arguments.thisValue, "keys")
        return JSMapIterator.create(realm, map, PropertyKind.Key)
    }

    fun set(realm: Realm, arguments: JSArguments): JSValue {
        val data = thisMapData(realm, arguments.thisValue, "set")
        val key = arguments.argument(0)
        data.map[key] = arguments.argument(1)
        data.keyInsertionOrder.add(key)
        return arguments.thisValue
    }

    fun values(realm: Realm, arguments: JSArguments): JSValue {
        val map = thisMapData(realm, arguments.thisValue, "values")
        return JSMapIterator.create(realm, map, PropertyKind.Value)
    }

    companion object {
        fun create(realm: Realm) = JSMapProto(realm).initialize()

        private fun thisMapData(realm: Realm, thisValue: JSValue, method: String): JSMapObject.MapData {
            if (!Operations.requireInternalSlot(thisValue, SlotName.MapData))
                Errors.IncompatibleMethodCall("Map.prototype.$method").throwTypeError(realm)
            return thisValue.getSlotAs(SlotName.MapData)
        }
    }
}
