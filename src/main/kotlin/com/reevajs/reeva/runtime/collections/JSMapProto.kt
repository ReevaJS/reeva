package com.reevajs.reeva.runtime.collections

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.iterators.JSMapIterator
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.JSEmpty
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.attrs
import com.reevajs.reeva.utils.key
import com.reevajs.reeva.utils.toValue

class JSMapProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "Map".toValue(), Descriptor.CONFIGURABLE)

        defineBuiltinGetter("size", ::getSize, attrs { +conf; -enum })
        defineBuiltin("clear", 0, ::clear)
        defineBuiltin("delete", 1, ::delete)
        defineBuiltin("entries", 0, ::entries)
        defineBuiltin("forEach", 1, ::forEach)
        defineBuiltin("get", 1, ::get)
        defineBuiltin("has", 1, ::has)
        defineBuiltin("keys", 1, ::keys)
        defineBuiltin("set", 2, ::set)
        defineBuiltin("values", 2, ::values)

        // "The initial value of the @@iterator property is the same function object
        // as the initial value of the 'entries' property."
        defineOwnProperty(Realm.WellKnownSymbols.iterator, internalGet("entries".key())!!.getRawValue(), attrs { +conf; +writ })
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSMapProto(realm).initialize()

        @ECMAImpl("24.1.3.1")
        @JvmStatic
        fun clear(arguments: JSArguments): JSValue {
            val data = thisMapData(arguments.thisValue, "clear")
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

        @ECMAImpl("24.1.3.3")
        @JvmStatic
        fun delete(arguments: JSArguments): JSValue {
            val data = thisMapData(arguments.thisValue, "delete")
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

        @ECMAImpl("24.1.3.4")
        @JvmStatic
        fun entries(arguments: JSArguments): JSValue {
            val data = thisMapData(arguments.thisValue, "entries")
            return JSMapIterator.create(data, PropertyKind.KeyValue)
        }

        @ECMAImpl("24.1.3.5")
        @JvmStatic
        fun forEach(arguments: JSArguments): JSValue {
            val data = thisMapData(arguments.thisValue, "forEach")
            val (callback, thisArg) = arguments.takeArgs(0..1)
            if (!AOs.isCallable(callback))
                Errors.Map.CallableFirstArg("forEach")

            data.iterationCount++

            var index = 0
            while (index < data.keyInsertionOrder.size) {
                val key = data.keyInsertionOrder[index]
                if (key != JSEmpty)
                    AOs.call(callback, thisArg, listOf(data.map[key]!!, key, arguments.thisValue))

                index++
            }

            data.iterationCount--

            return JSUndefined
        }

        @ECMAImpl("24.1.3.6")
        @JvmStatic
        fun get(arguments: JSArguments): JSValue {
            val data = thisMapData(arguments.thisValue, "get")
            return data.map[arguments.argument(0)] ?: JSUndefined
        }

        @ECMAImpl("24.1.3.7")
        @JvmStatic
        fun has(arguments: JSArguments): JSValue {
            val data = thisMapData(arguments.thisValue, "has")
            return (arguments.argument(0) in data.map).toValue()
        }

        @ECMAImpl("24.1.3.8")
        @JvmStatic
        fun keys(arguments: JSArguments): JSValue {
            val map = thisMapData(arguments.thisValue, "keys")
            return JSMapIterator.create(map, PropertyKind.Key)
        }

        @ECMAImpl("24.1.3.9")
        @JvmStatic
        fun set(arguments: JSArguments): JSValue {
            val data = thisMapData(arguments.thisValue, "set")
            val key = arguments.argument(0)
            data.map[key] = arguments.argument(1)
            data.keyInsertionOrder.add(key)
            return arguments.thisValue
        }

        @ECMAImpl("24.1.3.10")
        @JvmStatic
        fun getSize(arguments: JSArguments): JSValue {
            return thisMapData(arguments.thisValue, "size").map.size.toValue()
        }

        @ECMAImpl("24.1.3.11")
        @JvmStatic
        fun values(arguments: JSArguments): JSValue {
            val map = thisMapData(arguments.thisValue, "values")
            return JSMapIterator.create(map, PropertyKind.Value)
        }

        private fun thisMapData(thisValue: JSValue, method: String): MapData {
            if (!AOs.requireInternalSlot(thisValue, Slot.MapData))
                Errors.IncompatibleMethodCall("Map.prototype.$method").throwTypeError()
            return thisValue[Slot.MapData]
        }
    }
}
