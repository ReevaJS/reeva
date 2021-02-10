package me.mattco.reeva.runtime.memory

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.*
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.key
import me.mattco.reeva.utils.toValue

class JSTypedArrayCtor private constructor(realm: Realm) : JSNativeFunction(realm, "TypedArray", 0) {
    // To be used by the inherited typed array ctors, such as Int8Array
    fun inject(obj: JSObject) {
        obj.defineNativeFunction("from", 1, ::from)
        obj.defineNativeFunction("of", 0, ::of)
    }

    private fun from(arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        if (!thisValue.isConstructor)
            Errors.IncompatibleMethodCall("%TypedArray%.from").throwTypeError()

        val (source, mapfn, thisArg) = arguments.takeArgs(0..2)

        val mapping = if (mapfn != JSUndefined) {
            if (!Operations.isCallable(mapfn))
                Errors.NotCallable(mapfn.toPrintableString()).throwTypeError()
            true
        } else false

        val usingIterator = Operations.getMethod(source, Realm.`@@iterator`)
        if (usingIterator != JSUndefined) {
            val values = Operations.iterableToList(source, usingIterator)
            val targetObj = Operations.typedArrayCreate(thisValue, JSArguments(listOf(values.size.toValue())))
            values.forEachIndexed { index, value ->
                val mappedValue = if (mapping) {
                    Operations.call(mapfn, thisArg, listOf(value, index.toValue()))
                } else value
                Operations.set(targetObj, index.key(), mappedValue, true)
            }
            return targetObj
        }

        val arrayLike = source.toObject()
        val len = arrayLike.lengthOfArrayLike()
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

    private fun of(arguments: JSArguments): JSValue {
        if (!arguments.thisValue.isConstructor)
            Errors.IncompatibleMethodCall("%TypedArray%.of").throwTypeError()

        val newObj = Operations.typedArrayCreate(arguments.thisValue, JSArguments(listOf(arguments.size.toValue())))
        arguments.forEachIndexed { index, value ->
            Operations.set(newObj, index.key(), value, true)
        }
        return newObj
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget == JSUndefined) {
            Errors.NotCallable("TypedArray").throwTypeError()
        } else Errors.NotACtor("TypedArray").throwTypeError()
    }

    companion object {
        fun create(realm: Realm) = JSTypedArrayCtor(realm).initialize()
    }
}
