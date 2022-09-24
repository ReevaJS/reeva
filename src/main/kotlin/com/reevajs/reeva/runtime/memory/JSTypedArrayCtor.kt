package com.reevajs.reeva.runtime.memory

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.isConstructor
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.toObject
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.key
import com.reevajs.reeva.utils.toValue

class JSTypedArrayCtor private constructor(realm: Realm) : JSNativeFunction(realm, "TypedArray", 0) {
    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget == JSUndefined) {
            Errors.NotCallable("TypedArray").throwTypeError()
        } else Errors.NotACtor("TypedArray").throwTypeError()
    }

    companion object {
        fun create(realm: Realm) = JSTypedArrayCtor(realm).initialize()

        @ECMAImpl("23.2.2.1")
        @JvmStatic
        fun from(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (!thisValue.isConstructor)
                Errors.IncompatibleMethodCall("%TypedArray%.from").throwTypeError()

            val (source, mapfn, thisArg) = arguments.takeArgs(0..2)

            val mapping = if (mapfn != JSUndefined) {
                if (!Operations.isCallable(mapfn))
                    Errors.NotCallable(mapfn.toString()).throwTypeError()
                true
            } else false

            val usingIterator = Operations.getMethod(source, Realm.WellKnownSymbols.iterator)
            if (usingIterator != JSUndefined) {
                val values = Operations.iterableToList(source, usingIterator)
                val targetObj = Operations.typedArrayCreate(
                    thisValue,
                    JSArguments(listOf(values.size.toValue())),
                )
                values.forEachIndexed { index, value ->
                    val mappedValue = if (mapping) {
                        Operations.call(mapfn, thisArg, listOf(value, index.toValue()))
                    } else value
                    Operations.set(targetObj, index.key(), mappedValue, true)
                }
                return targetObj
            }

            val arrayLike = source.toObject()
            val len = Operations.lengthOfArrayLike(arrayLike)
            val targetObj = Operations.typedArrayCreate(thisValue, JSArguments(listOf(len.toValue())))
            for (index in 0 until len) {
                val value = arrayLike.get(index)
                val mappedValue = if (mapping) {
                    Operations.call(mapfn, thisArg, listOf(value, index.toValue()))
                } else value
                Operations.set(targetObj, index.key(), mappedValue, true)
            }

            return targetObj
        }

        @ECMAImpl("23.2.2.2")
        @JvmStatic
        fun of(arguments: JSArguments): JSValue {
            if (!arguments.thisValue.isConstructor)
                Errors.IncompatibleMethodCall("%TypedArray%.of").throwTypeError()

            val newObj = Operations.typedArrayCreate(
                arguments.thisValue,
                JSArguments(listOf(arguments.size.toValue())),
            )
            arguments.forEachIndexed { index, value ->
                Operations.set(newObj, index.key(), value, true)
            }
            return newObj
        }
    }
}
