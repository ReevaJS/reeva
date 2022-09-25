package com.reevajs.reeva.runtime.collections

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.annotations.ECMAImpl
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

        defineBuiltinGetter("size", ::getSize, attrs { +conf; -enum })
        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "Set".toValue(), Descriptor.CONFIGURABLE)
        defineBuiltin("add", 1, ::add)
        defineBuiltin("clear", 1, ::clear)
        defineBuiltin("delete", 1, ::delete)
        defineBuiltin("entries", 1, ::entries)
        defineBuiltin("forEach", 1, ::forEach)
        defineBuiltin("has", 1, ::has)
        defineBuiltin("values", 1, ::values)

        // "The initial value of the 'keys' property is the same function object as the initial value
        // of the 'values' property"
        defineOwnProperty("keys", internalGet("values".key())!!.getRawValue())

        // "The initial value of the @@iterator property is the same function object as the initial value
        // of the 'values' property"
        defineOwnProperty(Realm.WellKnownSymbols.iterator, internalGet("values".key())!!.getRawValue())
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSSetProto(realm).initialize()

        private fun thisSetObject(thisValue: JSValue, method: String): JSSetObject.SetData {
            if (!AOs.requireInternalSlot(thisValue, SlotName.SetData))
                Errors.IncompatibleMethodCall("Set.prototype.$method").throwTypeError()
            return thisValue.getSlot(SlotName.SetData)
        }

        @ECMAImpl("24.2.3.1")
        @JvmStatic
        fun add(arguments: JSArguments): JSValue {
            val data = thisSetObject(arguments.thisValue, "add")
            data.set.add(arguments.argument(0))
            data.insertionOrder.add(arguments.argument(0))
            return arguments.thisValue
        }

        @ECMAImpl("24.2.3.2")
        @JvmStatic
        fun clear(arguments: JSArguments): JSValue {
            val data = thisSetObject(arguments.thisValue, "clear")
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
        fun delete(arguments: JSArguments): JSValue {
            val data = thisSetObject(arguments.thisValue, "delete")
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
        fun entries(arguments: JSArguments): JSValue {
            val data = thisSetObject(arguments.thisValue, "entries")
            return JSSetIterator.create(data, PropertyKind.KeyValue)
        }

        @ECMAImpl("24.2.3.6")
        @JvmStatic
        fun forEach(arguments: JSArguments): JSValue {
            val data = thisSetObject(arguments.thisValue, "forEach")
            val (callback, thisArg) = arguments.takeArgs(0..1)
            if (!AOs.isCallable(callback))
                Errors.Set.FirstArgNotCallable("forEach").throwTypeError()

            data.iterationCount++

            var index = 0
            while (index < data.insertionOrder.size) {
                val value = data.insertionOrder[index]
                if (value != JSEmpty)
                    AOs.call(callback, thisArg, listOf(value, value, arguments.thisValue))

                index++
            }

            data.iterationCount--

            return JSUndefined
        }

        @ECMAImpl("24.2.3.7")
        @JvmStatic
        fun has(arguments: JSArguments): JSValue {
            val data = thisSetObject(arguments.thisValue, "has")
            return (arguments.argument(0) in data.set).toValue()
        }

        @ECMAImpl("24.2.3.9")
        @JvmStatic
        fun getSize(arguments: JSArguments): JSValue {
            return thisSetObject(arguments.thisValue, "getSize").set.size.toValue()
        }

        @ECMAImpl("24.2.3.10")
        @JvmStatic
        fun values(arguments: JSArguments): JSValue {
            val data = thisSetObject(arguments.thisValue, "values")
            return JSSetIterator.create(data, PropertyKind.Value)
        }
    }
}
