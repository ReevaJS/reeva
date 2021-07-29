package com.reevajs.reeva.runtime.collections

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.builtins.Builtin
import com.reevajs.reeva.runtime.iterators.JSSetIterator
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSEmpty
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.attrs
import com.reevajs.reeva.utils.key
import com.reevajs.reeva.utils.toValue

class JSSetProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineBuiltinAccessor("size", attrs { +conf - enum }, Builtin.SetProtoGetSize, null)
        defineOwnProperty(Realm.`@@toStringTag`, "Set".toValue(), Descriptor.CONFIGURABLE)
        defineBuiltin("add", 1, Builtin.SetProtoAdd)
        defineBuiltin("clear", 1, Builtin.SetProtoClear)
        defineBuiltin("delete", 1, Builtin.SetProtoDelete)
        defineBuiltin("entries", 1, Builtin.SetProtoEntries)
        defineBuiltin("forEach", 1, Builtin.SetProtoForEach)
        defineBuiltin("has", 1, Builtin.SetProtoHas)
        defineBuiltin("values", 1, Builtin.SetProtoValues)

        // "The initial value of the 'keys' property is the same function object as the initial value
        // of the 'values' property"
        defineBuiltin("keys", 0, Builtin.SetProtoValues)
        // "The initial value of the @@iterator property is the same function object as the initial value
        // of the 'values' property"
        defineBuiltin(Realm.`@@iterator`.key(), 0, Builtin.SetProtoValues)
    }

    companion object {
        fun create(realm: Realm) = JSSetProto(realm).initialize()

        private fun thisSetObject(realm: Realm, thisValue: JSValue, method: String): JSSetObject.SetData {
            if (!Operations.requireInternalSlot(thisValue, SlotName.SetData))
                Errors.IncompatibleMethodCall("Set.prototype.$method").throwTypeError(realm)
            return thisValue.getSlotAs(SlotName.SetData)
        }

        @ECMAImpl("24.2.3.1")
        @JvmStatic
        fun add(realm: Realm, arguments: JSArguments): JSValue {
            val data = thisSetObject(realm, arguments.thisValue, "add")
            data.set.add(arguments.argument(0))
            data.insertionOrder.add(arguments.argument(0))
            return arguments.thisValue
        }

        @ECMAImpl("24.2.3.2")
        @JvmStatic
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

        @ECMAImpl("24.2.3.4")
        @JvmStatic
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

        @ECMAImpl("24.2.3.5")
        @JvmStatic
        fun entries(realm: Realm, arguments: JSArguments): JSValue {
            val data = thisSetObject(realm, arguments.thisValue, "entries")
            return JSSetIterator.create(realm, data, PropertyKind.KeyValue)
        }

        @ECMAImpl("24.2.3.6")
        @JvmStatic
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

        @ECMAImpl("24.2.3.7")
        @JvmStatic
        fun has(realm: Realm, arguments: JSArguments): JSValue {
            val data = thisSetObject(realm, arguments.thisValue, "has")
            return (arguments.argument(0) in data.set).toValue()
        }

        @ECMAImpl("24.2.3.9")
        @JvmStatic
        fun getSize(realm: Realm, thisValue: JSValue): JSValue {
            return thisSetObject(realm, thisValue, "getSize").set.size.toValue()
        }

        @ECMAImpl("24.2.3.10")
        @JvmStatic
        fun values(realm: Realm, arguments: JSArguments): JSValue {
            val data = thisSetObject(realm, arguments.thisValue, "values")
            return JSSetIterator.create(realm, data, PropertyKind.Value)
        }
    }
}
