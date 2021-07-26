package com.reevajs.reeva.runtime.memory

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.key
import com.reevajs.reeva.utils.toValue

class JSTypedArrayCtor private constructor(realm: Realm) : JSNativeFunction(realm, "TypedArray", 0) {
    // To be used by the inherited typed array ctors, such as Int8Array
    fun inject(obj: JSObject) {
        obj.defineNativeFunction("from", 1, ::from)
        obj.defineNativeFunction("of", 0, ::of)
    }

    private fun from(realm: Realm, arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        if (!thisValue.isConstructor)
            Errors.IncompatibleMethodCall("%TypedArray%.from").throwTypeError(realm)

        val (source, mapfn, thisArg) = arguments.takeArgs(0..2)

        val mapping = if (mapfn != JSUndefined) {
            if (!Operations.isCallable(mapfn))
                Errors.NotCallable(mapfn.toPrintableString()).throwTypeError(realm)
            true
        } else false

        val usingIterator = Operations.getMethod(realm, source, Realm.`@@iterator`)
        if (usingIterator != JSUndefined) {
            val values = Operations.iterableToList(realm, source, usingIterator)
            val targetObj = Operations.typedArrayCreate(realm, thisValue, JSArguments(listOf(values.size.toValue())))
            values.forEachIndexed { index, value ->
                val mappedValue = if (mapping) {
                    Operations.call(realm, mapfn, thisArg, listOf(value, index.toValue()))
                } else value
                Operations.set(realm, targetObj, index.key(), mappedValue, true)
            }
            return targetObj
        }

        val arrayLike = source.toObject(realm)
        val len = arrayLike.lengthOfArrayLike(realm)
        val targetObj = Operations.typedArrayCreate(realm, thisValue, JSArguments(listOf(len.toValue())))
        for (index in 0 until len) {
            val value = arrayLike.get(index)
            val mappedValue = if (mapping) {
                Operations.call(realm, mapfn, thisArg, listOf(value, index.toValue()))
            } else value
            Operations.set(realm, targetObj, index.key(), mappedValue, true)
        }

        return targetObj
    }

    private fun of(realm: Realm, arguments: JSArguments): JSValue {
        if (!arguments.thisValue.isConstructor)
            Errors.IncompatibleMethodCall("%TypedArray%.of").throwTypeError(realm)

        val newObj = Operations.typedArrayCreate(realm, arguments.thisValue, JSArguments(listOf(arguments.size.toValue())))
        arguments.forEachIndexed { index, value ->
            Operations.set(realm, newObj, index.key(), value, true)
        }
        return newObj
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget == JSUndefined) {
            Errors.NotCallable("TypedArray").throwTypeError(realm)
        } else Errors.NotACtor("TypedArray").throwTypeError(realm)
    }

    companion object {
        fun create(realm: Realm) = JSTypedArrayCtor(realm).initialize()
    }
}
