package com.reevajs.reeva.runtime.arrays

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.errors.*
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.errors.JSTypeErrorObject
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSEmpty
import com.reevajs.reeva.runtime.primitives.JSFalse
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.toObject
import com.reevajs.reeva.runtime.toUint32
import com.reevajs.reeva.utils.*

class JSArrayCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Array", 1) {
    override fun init() {
        super.init()

        defineBuiltinGetter(Realm.WellKnownSymbols.species, ::getSymbolSpecies, attrs { +conf; -enum; -writ })

        defineBuiltin("isArray", 1, ::isArray)
        defineBuiltin("from", 1, ::from)
        defineBuiltin("of", 0, ::of)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        val realNewTarget = arguments.newTarget.ifUndefined(this)

        val proto = AOs.getPrototypeFromConstructor(realNewTarget, Realm::arrayProto)

        return when (arguments.size) {
            0 -> JSArrayObject.create(proto = proto)
            1 -> {
                val array = JSArrayObject.create(proto = proto)
                val lengthArg = arguments[0]
                val length = if (lengthArg.isNumber) {
                    val intLen = lengthArg.toUint32()
                    // TODO: The spec says "if intLen is not the same value as len...", does that refer to the
                    // operation SameValue? Or is it different?
                    if (!intLen.sameValue(lengthArg))
                        Errors.InvalidArrayLength(lengthArg.toString()).throwRangeError()
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
                val array = AOs.arrayCreate(arguments.size, proto)
                arguments.forEachIndexed { index, value ->
                    array.indexedProperties.set(array, index, value)
                }
                expect(array.indexedProperties.arrayLikeSize == arguments.size.toLong())
                array
            }
        }
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSArrayCtor(realm).initialize()

        fun create(length: Int, realm: Realm = Agent.activeAgent.getActiveRealm()) =
            JSArrayCtor(realm).initialize().also {
                it.indexedProperties.setArrayLikeSize(length.toLong())
            }

        fun create(length: Long, realm: Realm = Agent.activeAgent.getActiveRealm()) =
            JSArrayCtor(realm).initialize().also {
                it.indexedProperties.setArrayLikeSize(length)
            }

        @ECMAImpl("23.1.2.1")
        @JvmStatic
        fun from(arguments: JSArguments): JSValue {
            val items = arguments.argument(0)
            val mapFn = arguments.argument(1)
            val thisArg = arguments.argument(2)

            val mapping = if (mapFn == JSUndefined) false else {
                if (!AOs.isCallable(mapFn))
                    Errors.NotCallable(mapFn.toString()).throwTypeError()
                true
            }

            val usingIterator = AOs.getMethod(items, Realm.WellKnownSymbols.iterator)
            if (usingIterator != JSUndefined) {
                val array = if (AOs.isConstructor(arguments.thisValue)) {
                    AOs.construct(arguments.thisValue) as JSObject
                } else AOs.arrayCreate(0)

                val iteratorRecord =
                    AOs.getIterator(items, AOs.IteratorHint.Sync, usingIterator as JSFunction)
                var k = 0L
                while (true) {
                    if (k == AOs.MAX_SAFE_INTEGER) {
                        val error = completion<JSValue> { Errors.InvalidArrayLength(Long.MAX_VALUE).throwTypeError() }
                        return AOs.iteratorClose(iteratorRecord, error)
                    }

                    val next = AOs.iteratorStep(iteratorRecord)
                    if (next == JSFalse) {
                        AOs.set(array, "length".key(), k.toValue(), true)
                        return array
                    }

                    val nextValue = AOs.iteratorValue(next)
                    val mappedValue = if (mapping) {
                        AOs.ifAbruptCloseIterator(completion { 
                            AOs.call(mapFn, thisArg, listOf(nextValue, k.toValue()))
                        }, iteratorRecord) { return it }
                    } else nextValue

                    AOs.ifAbruptCloseIterator(completion<JSValue> {
                        AOs.createDataPropertyOrThrow(array, k.key(), mappedValue)
                        JSEmpty
                    }, iteratorRecord) { return it }

                    k++
                }
            }

            val arrayLike = items.toObject()
            val len = AOs.lengthOfArrayLike(arrayLike)
            val array = if (AOs.isConstructor(arguments.thisValue)) {
                AOs.construct(arguments.thisValue, listOf(len.toValue())) as JSObject
            } else AOs.arrayCreate(len)

            var k = 0
            while (k < len) {
                val kValue = arrayLike.get(k)
                val mappedValue = if (mapping) {
                    AOs.call(mapFn, thisArg, listOf(kValue, k.toValue()))
                } else kValue
                AOs.createDataPropertyOrThrow(array, k.key(), mappedValue)
                k++
            }

            AOs.set(array, "length".key(), len.toValue(), true)
            return array
        }

        @ECMAImpl("23.1.2.2")
        @JvmStatic
        fun isArray(arguments: JSArguments): JSValue {
            return AOs.isArray(arguments.argument(0)).toValue()
        }

        @ECMAImpl("23.1.2.3")
        @JvmStatic
        fun of(arguments: JSArguments): JSValue {
            val array = if (AOs.isConstructor(arguments.thisValue)) {
                AOs.construct(arguments.thisValue, listOf(arguments.size.toValue())) as JSObject
            } else AOs.arrayCreate(arguments.size)

            arguments.forEachIndexed { index, value ->
                AOs.createDataPropertyOrThrow(array, index.key(), value)
            }
            AOs.set(array, "length".key(), arguments.size.toValue(), true)
            return array
        }

        @ECMAImpl("23.1.2.5")
        @JvmStatic
        fun getSymbolSpecies(arguments: JSArguments): JSValue {
            return arguments.thisValue
        }
    }
}
