package me.mattco.reeva.runtime.builtins

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
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
        return thisMapObject(thisValue, "size").mapData.size.toValue()
    }

    fun clear(thisValue: JSValue, arguments: JSArguments): JSValue {
        val map = thisMapObject(thisValue, "clear")
        map.mapData.clear()
        if (map.iterationCount == 0) {
            map.keyInsertionOrder.clear()
        } else {
            map.keyInsertionOrder.indices.forEach {
                map.keyInsertionOrder[it] = JSEmpty
            }
        }
        return JSUndefined
    }

    fun delete(thisValue: JSValue, arguments: JSArguments): JSValue {
        val map = thisMapObject(thisValue, "delete")
        val key = arguments.argument(0)
        if (map.iterationCount == 0) {
            map.keyInsertionOrder.remove(key)
        } else {
            val index = map.keyInsertionOrder.indexOf(key)
            if (index == -1)
                return false.toValue()
            map.keyInsertionOrder[index] = JSEmpty
        }

        return (map.mapData.remove(key) != null).toValue()
    }

    fun entries(thisValue: JSValue, arguments: JSArguments): JSValue {
        val map = thisMapObject(thisValue, "entries")
        return JSMapIterator.create(realm, map, PropertyKind.KeyValue)
    }

    fun forEach(thisValue: JSValue, arguments: JSArguments): JSValue {
        val map = thisMapObject(thisValue, "forEach")
        val (callback, thisArg) = arguments.takeArgs(0..1)
        if (!Operations.isCallable(callback))
            Errors.Map.CallableFirstArg("forEach")

        map.iterationCount++

        var index = 0
        while (index < map.keyInsertionOrder.size) {
            val key = map.keyInsertionOrder[index]
            if (key != JSEmpty)
                Operations.call(callback, thisArg, listOf(map.mapData[key]!!, key, map))

            index++
        }

        map.iterationCount--

        return JSUndefined
    }

    fun get(thisValue: JSValue, arguments: JSArguments): JSValue {
        val map = thisMapObject(thisValue, "get")
        return map.mapData[arguments.argument(0)] ?: JSUndefined
    }

    fun has(thisValue: JSValue, arguments: JSArguments): JSValue {
        val map = thisMapObject(thisValue, "has")
        return (arguments.argument(0) in map.mapData).toValue()
    }

    fun keys(thisValue: JSValue, arguments: JSArguments): JSValue {
        val map = thisMapObject(thisValue, "keys")
        return JSMapIterator.create(realm, map, PropertyKind.Key)
    }

    fun set(thisValue: JSValue, arguments: JSArguments): JSValue {
        val map = thisMapObject(thisValue, "set")
        val key = arguments.argument(0)
        map.mapData[key] = arguments.argument(1)
        map.keyInsertionOrder.add(key)
        return map
    }

    fun values(thisValue: JSValue, arguments: JSArguments): JSValue {
        val map = thisMapObject(thisValue, "values")
        return JSMapIterator.create(realm, map, PropertyKind.Value)
    }

    companion object {
        fun create(realm: Realm) = JSMapProto(realm).initialize()

        private fun thisMapObject(thisValue: JSValue, method: String): JSMapObject {
            if (thisValue !is JSMapObject)
                Errors.IncompatibleMethodCall("Map.prototype.$method").throwTypeError()
            return thisValue
        }
    }
}
