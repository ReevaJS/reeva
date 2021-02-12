package me.mattco.reeva.runtime.arrays

import me.mattco.reeva.Reeva
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSFalse
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.*

class JSArrayCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Array", 1) {
    init {
        isConstructable = true
    }

    override fun init() {
        super.init()

        defineNativeAccessor(Realm.`@@species`.key(), attrs { +conf -enum }, ::`get@@species`, null, "get [Symbol.species]")
        defineNativeFunction("isArray", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE, ::isArray)
        defineNativeFunction("from", 1, ::from)
        defineNativeFunction("of", 0, ::of)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        val realNewTarget = arguments.newTarget.let {
            if (it is JSUndefined) {
                // TODO: "If NewTarget is undefined, let newTarget be the active function
                //  object; else let newTarget be NewTarget."
                JSUndefined
            } else it
        }

        val proto = Operations.getPrototypeFromConstructor(realNewTarget, realm.arrayProto)

        return when (arguments.size) {
            0 -> JSArrayObject.create(realm, proto)
            1 -> {
                val array = JSArrayObject.create(realm, proto)
                val lengthArg = arguments[0]
                val length = if (lengthArg.isNumber) {
                    val intLen = Operations.toUint32(lengthArg)
                    // TODO: The spec says "if intLen is not the same value as len...", does that refer to the
                    // operation SameValue? Or is it different?
                    if (!intLen.sameValue(lengthArg))
                        Errors.InvalidArrayLength(Operations.toPrintableString(lengthArg)).throwRangeError()
                    intLen.asInt
                } else {
                    array.set(0, lengthArg)
                    1
                }
                array.also {
                    it.indexedProperties.setArrayLikeSize(length.toLong())
                }
            }
            else -> {
                val array = Operations.arrayCreate(arguments.size, proto)
                arguments.forEachIndexed { index, value ->
                    array.indexedProperties.set(array, index, value)
                }
                expect(array.indexedProperties.arrayLikeSize == arguments.size.toLong())
                array
            }
        }
    }

    fun `get@@species`(thisValue: JSValue): JSValue {
        return thisValue
    }

    fun isArray(arguments: JSArguments): JSValue {
        return Operations.isArray(arguments.argument(0)).toValue()
    }

    fun from(arguments: JSArguments): JSValue {
        val items = arguments.argument(0)
        val mapFn = arguments.argument(1)
        val thisArg = arguments.argument(2)

        val mapping = if (mapFn == JSUndefined) false else {
            if (!Operations.isCallable(mapFn))
                Errors.NotCallable(Operations.toPrintableString(mapFn)).throwTypeError()
            true
        }

        val usingIterator = Operations.getMethod(items, Realm.`@@iterator`)
        if (usingIterator != JSUndefined) {
            val array = (if (Operations.isConstructor(arguments.thisValue)) {
                Operations.construct(arguments.thisValue)
            } else Operations.arrayCreate(0)) as JSObject

            val iteratorRecord = Operations.getIterator(items, Operations.IteratorHint.Sync, usingIterator as JSFunction)
            var k = 0L
            while (true) {
                if (k == Operations.MAX_SAFE_INTEGER) {
                    return Operations.iteratorClose(
                        iteratorRecord,
                        JSTypeErrorObject.create(
                            Reeva.activeAgent.runningContext.realm,
                            "array length ${Long.MAX_VALUE} is too large"
                        )
                    )
                }

                val next = Operations.iteratorStep(iteratorRecord)
                if (next == JSFalse) {
                    Operations.set(array, "length".key(), k.toValue(), true)
                    return array
                }

                val nextValue = Operations.iteratorValue(next)
                val mappedValue = if (mapping) {
                    try {
                        Operations.call(mapFn, thisArg, listOf(nextValue, k.toValue()))
                    } catch (e: ThrowException) {
                        return Operations.iteratorClose(iteratorRecord, e.value)
                    }
                } else nextValue

                try {
                    Operations.createDataPropertyOrThrow(array, k.key(), mappedValue)
                } catch (e: ThrowException) {
                    return Operations.iteratorClose(iteratorRecord, e.value)
                }

                k++
            }
        }

        val arrayLike = Operations.toObject(items)
        val len = Operations.lengthOfArrayLike(arrayLike)
        val array = (if (Operations.isConstructor(arguments.thisValue)) {
            Operations.construct(arguments.thisValue, listOf(len.toValue()))
        } else Operations.arrayCreate(len)) as JSObject

        var k = 0
        while (k < len) {
            val kValue = arrayLike.get(k)
            val mappedValue = if (mapping) {
                Operations.call(mapFn, thisArg, listOf(kValue, k.toValue()))
            } else kValue
            Operations.createDataPropertyOrThrow(array, k.key(), mappedValue)
            k++
        }

        Operations.set(array, "length".key(), len.toValue(), true)
        return array
    }

    fun of(arguments: JSArguments): JSValue {
        val array = (if (Operations.isConstructor(arguments.thisValue)) {
            Operations.construct(arguments.thisValue, listOf(arguments.size.toValue()))
        } else Operations.arrayCreate(arguments.size)) as JSObject

        arguments.forEachIndexed { index, value ->
            Operations.createDataPropertyOrThrow(array, index.key(), value)
        }
        Operations.set(array, "length".key(), arguments.size.toValue(), true)
        return array
    }

    companion object {
        fun create(realm: Realm) = JSArrayCtor(realm).initialize()
        fun create(realm: Realm, length: Int) = JSArrayCtor(realm).initialize().also {
            it.indexedProperties.setArrayLikeSize(length.toLong())
        }
        fun create(realm: Realm, length: Long) = JSArrayCtor(realm).initialize().also {
            it.indexedProperties.setArrayLikeSize(length)
        }
    }
}
