package com.reevajs.reeva.runtime.collections

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.builtins.Builtin
import com.reevajs.reeva.runtime.iterators.JSMapIterator
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
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
        defineBuiltinAccessor("size", attrs { +conf - enum }, Builtin.MapProtoGetSize, null)
        defineBuiltin("clear", 0, Builtin.MapProtoClear)
        defineBuiltin("delete", 1, Builtin.MapProtoDelete)
        defineBuiltin("entries", 0, Builtin.MapProtoEntries)
        defineBuiltin("forEach", 1, Builtin.MapProtoForEach)
        defineBuiltin("get", 1, Builtin.MapProtoGet)
        defineBuiltin("has", 1, Builtin.MapProtoHas)
        defineBuiltin("keys", 1, Builtin.MapProtoKeys)
        defineBuiltin("set", 2, Builtin.MapProtoSet)
        defineBuiltin("values", 2, Builtin.MapProtoValues)

        // "The initial value of the @@iterator property is the same function object
        // as the initial value of the 'entries' property."
        defineBuiltin(
            Realm.`@@iterator`.key(),
            0,
            Builtin.MapProtoEntries,
            Descriptor.CONFIGURABLE or Descriptor.WRITABLE
        )
    }

    companion object {
        fun create(realm: Realm) = JSMapProto(realm).initialize()

        @ECMAImpl("24.1.3.1")
        @JvmStatic
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

        @ECMAImpl("24.1.3.3")
        @JvmStatic
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

        @ECMAImpl("24.1.3.4")
        @JvmStatic
        fun entries(realm: Realm, arguments: JSArguments): JSValue {
            val data = thisMapData(realm, arguments.thisValue, "entries")
            return JSMapIterator.create(realm, data, PropertyKind.KeyValue)
        }

        @ECMAImpl("24.1.3.5")
        @JvmStatic
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

        @ECMAImpl("24.1.3.6")
        @JvmStatic
        fun get(realm: Realm, arguments: JSArguments): JSValue {
            val data = thisMapData(realm, arguments.thisValue, "get")
            return data.map[arguments.argument(0)] ?: JSUndefined
        }

        @ECMAImpl("24.1.3.7")
        @JvmStatic
        fun has(realm: Realm, arguments: JSArguments): JSValue {
            val data = thisMapData(realm, arguments.thisValue, "has")
            return (arguments.argument(0) in data.map).toValue()
        }

        @ECMAImpl("24.1.3.8")
        @JvmStatic
        fun keys(realm: Realm, arguments: JSArguments): JSValue {
            val map = thisMapData(realm, arguments.thisValue, "keys")
            return JSMapIterator.create(realm, map, PropertyKind.Key)
        }

        @ECMAImpl("24.1.3.9")
        @JvmStatic
        fun set(realm: Realm, arguments: JSArguments): JSValue {
            val data = thisMapData(realm, arguments.thisValue, "set")
            val key = arguments.argument(0)
            data.map[key] = arguments.argument(1)
            data.keyInsertionOrder.add(key)
            return arguments.thisValue
        }

        @ECMAImpl("24.1.3.10")
        @JvmStatic
        fun getSize(realm: Realm, thisValue: JSValue): JSValue {
            return thisMapData(realm, thisValue, "size").map.size.toValue()
        }

        @ECMAImpl("24.1.3.11")
        @JvmStatic
        fun values(realm: Realm, arguments: JSArguments): JSValue {
            val map = thisMapData(realm, arguments.thisValue, "values")
            return JSMapIterator.create(realm, map, PropertyKind.Value)
        }

        private fun thisMapData(realm: Realm, thisValue: JSValue, method: String): JSMapObject.MapData {
            if (!Operations.requireInternalSlot(thisValue, SlotName.MapData))
                Errors.IncompatibleMethodCall("Map.prototype.$method").throwTypeError(realm)
            return thisValue.getSlotAs(SlotName.MapData)
        }
    }
}
