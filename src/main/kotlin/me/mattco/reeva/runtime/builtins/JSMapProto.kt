package me.mattco.reeva.runtime.builtins

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.SlotName
import me.mattco.reeva.runtime.iterators.JSMapIterator
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSEmpty
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.*

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

    fun getSize(thisValue: JSValue): JSValue {
        return thisMapData(thisValue, "size").map.size.toValue()
    }

    fun clear(thisValue: JSValue, arguments: JSArguments): JSValue {
        val data = thisMapData(thisValue, "clear")
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

    fun delete(thisValue: JSValue, arguments: JSArguments): JSValue {
        val data = thisMapData(thisValue, "delete")
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

    fun entries(thisValue: JSValue, arguments: JSArguments): JSValue {
        val data = thisMapData(thisValue, "entries")
        return JSMapIterator.create(realm, data, PropertyKind.KeyValue)
    }

    fun forEach(thisValue: JSValue, arguments: JSArguments): JSValue {
        val data = thisMapData(thisValue, "forEach")
        val (callback, thisArg) = arguments.takeArgs(0..1)
        if (!Operations.isCallable(callback))
            Errors.Map.CallableFirstArg("forEach")

        data.iterationCount++

        var index = 0
        while (index < data.keyInsertionOrder.size) {
            val key = data.keyInsertionOrder[index]
            if (key != JSEmpty)
                Operations.call(callback, thisArg, listOf(data.map[key]!!, key, thisValue))

            index++
        }

        data.iterationCount--

        return JSUndefined
    }

    fun get(thisValue: JSValue, arguments: JSArguments): JSValue {
        val data = thisMapData(thisValue, "get")
        return data.map[arguments.argument(0)] ?: JSUndefined
    }

    fun has(thisValue: JSValue, arguments: JSArguments): JSValue {
        val data = thisMapData(thisValue, "has")
        return (arguments.argument(0) in data.map).toValue()
    }

    fun keys(thisValue: JSValue, arguments: JSArguments): JSValue {
        val map = thisMapData(thisValue, "keys")
        return JSMapIterator.create(realm, map, PropertyKind.Key)
    }

    fun set(thisValue: JSValue, arguments: JSArguments): JSValue {
        val data = thisMapData(thisValue, "set")
        val key = arguments.argument(0)
        data.map[key] = arguments.argument(1)
        data.keyInsertionOrder.add(key)
        return thisValue
    }

    fun values(thisValue: JSValue, arguments: JSArguments): JSValue {
        val map = thisMapData(thisValue, "values")
        return JSMapIterator.create(realm, map, PropertyKind.Value)
    }

    companion object {
        fun create(realm: Realm) = JSMapProto(realm).initialize()

        private fun thisMapData(thisValue: JSValue, method: String): JSMapObject.MapData {
            if (!Operations.requireInternalSlot(thisValue, SlotName.MapData))
                Errors.IncompatibleMethodCall("Map.prototype.$method").throwTypeError()
            return thisValue.getSlotAs(SlotName.MapData)
        }
    }
}
