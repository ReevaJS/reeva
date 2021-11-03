package com.reevajs.reeva.runtime.arrays

import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.core.errors.ThrowException
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.errors.JSTypeErrorObject
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSFalse
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.*

class JSArrayCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Array", 1) {
    override fun init() {
        super.init()

        defineBuiltinGetter(Realm.`@@species`, ReevaBuiltin.ArrayCtorGetSymbolSpecies, attrs { +conf; -enum; -writ })

        defineBuiltin("isArray", 1, ReevaBuiltin.ArrayCtorIsArray)
        defineBuiltin("from", 1, ReevaBuiltin.ArrayCtorFrom)
        defineBuiltin("of", 0, ReevaBuiltin.ArrayCtorOf)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        val realNewTarget = arguments.newTarget.ifUndefined(this)

        val proto = Operations.getPrototypeFromConstructor(realNewTarget, realm.arrayProto)

        return when (arguments.size) {
            0 -> JSArrayObject.create(realm, proto)
            1 -> {
                val array = JSArrayObject.create(realm, proto)
                val lengthArg = arguments[0]
                val length = if (lengthArg.isNumber) {
                    val intLen = Operations.toUint32(realm, lengthArg)
                    // TODO: The spec says "if intLen is not the same value as len...", does that refer to the
                    // operation SameValue? Or is it different?
                    if (!intLen.sameValue(lengthArg))
                        Errors.InvalidArrayLength(Operations.toPrintableString(lengthArg)).throwRangeError(realm)
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
                val array = Operations.arrayCreate(realm, arguments.size, proto)
                arguments.forEachIndexed { index, value ->
                    array.indexedProperties.set(array, index, value)
                }
                expect(array.indexedProperties.arrayLikeSize == arguments.size.toLong())
                array
            }
        }
    }

    companion object {
        fun create(realm: Realm) = JSArrayCtor(realm).initialize()
        fun create(realm: Realm, length: Int) = JSArrayCtor(realm).initialize().also {
            it.indexedProperties.setArrayLikeSize(length.toLong())
        }

        fun create(realm: Realm, length: Long) = JSArrayCtor(realm).initialize().also {
            it.indexedProperties.setArrayLikeSize(length)
        }

        @ECMAImpl("23.1.2.1")
        @JvmStatic
        fun from(realm: Realm, arguments: JSArguments): JSValue {
            val items = arguments.argument(0)
            val mapFn = arguments.argument(1)
            val thisArg = arguments.argument(2)

            val mapping = if (mapFn == JSUndefined) false else {
                if (!Operations.isCallable(mapFn))
                    Errors.NotCallable(Operations.toPrintableString(mapFn)).throwTypeError(realm)
                true
            }

            val usingIterator = Operations.getMethod(realm, items, Realm.`@@iterator`)
            if (usingIterator != JSUndefined) {
                val array = if (Operations.isConstructor(arguments.thisValue)) {
                    Operations.construct(arguments.thisValue) as JSObject
                } else Operations.arrayCreate(realm, 0)

                val iteratorRecord =
                    Operations.getIterator(realm, items, Operations.IteratorHint.Sync, usingIterator as JSFunction)
                var k = 0L
                while (true) {
                    if (k == Operations.MAX_SAFE_INTEGER) {
                        return Operations.iteratorClose(
                            iteratorRecord,
                            JSTypeErrorObject.create(
                                realm,
                                "array length ${Long.MAX_VALUE} is too large"
                            )
                        )
                    }

                    val next = Operations.iteratorStep(iteratorRecord)
                    if (next == JSFalse) {
                        Operations.set(realm, array, "length".key(), k.toValue(), true)
                        return array
                    }

                    val nextValue = Operations.iteratorValue(next)
                    val mappedValue = if (mapping) {
                        try {
                            Operations.call(realm, mapFn, thisArg, listOf(nextValue, k.toValue()))
                        } catch (e: ThrowException) {
                            return Operations.iteratorClose(iteratorRecord, e.value)
                        }
                    } else nextValue

                    try {
                        Operations.createDataPropertyOrThrow(realm, array, k.key(), mappedValue)
                    } catch (e: ThrowException) {
                        return Operations.iteratorClose(iteratorRecord, e.value)
                    }

                    k++
                }
            }

            val arrayLike = Operations.toObject(realm, items)
            val len = Operations.lengthOfArrayLike(realm, arrayLike)
            val array = if (Operations.isConstructor(arguments.thisValue)) {
                Operations.construct(arguments.thisValue, listOf(len.toValue())) as JSObject
            } else Operations.arrayCreate(realm, len)

            var k = 0
            while (k < len) {
                val kValue = arrayLike.get(k)
                val mappedValue = if (mapping) {
                    Operations.call(realm, mapFn, thisArg, listOf(kValue, k.toValue()))
                } else kValue
                Operations.createDataPropertyOrThrow(realm, array, k.key(), mappedValue)
                k++
            }

            Operations.set(realm, array, "length".key(), len.toValue(), true)
            return array
        }

        @ECMAImpl("23.1.2.2")
        @JvmStatic
        fun isArray(realm: Realm, arguments: JSArguments): JSValue {
            return Operations.isArray(realm, arguments.argument(0)).toValue()
        }

        @ECMAImpl("23.1.2.3")
        @JvmStatic
        fun of(realm: Realm, arguments: JSArguments): JSValue {
            val array = if (Operations.isConstructor(arguments.thisValue)) {
                Operations.construct(arguments.thisValue, listOf(arguments.size.toValue())) as JSObject
            } else Operations.arrayCreate(realm, arguments.size)

            arguments.forEachIndexed { index, value ->
                Operations.createDataPropertyOrThrow(realm, array, index.key(), value)
            }
            Operations.set(realm, array, "length".key(), arguments.size.toValue(), true)
            return array
        }

        @ECMAImpl("23.1.2.5")
        @JvmStatic
        fun `get@@species`(realm: Realm, arguments: JSArguments): JSValue {
            return arguments.thisValue
        }
    }
}
