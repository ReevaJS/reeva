package com.reevajs.reeva.runtime.memory

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.arrays.JSArrayProto
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.JSNumber
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.*
import kotlin.math.min

class JSTypedArrayProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineBuiltinGetter(
            Realm.WellKnownSymbols.toStringTag,
            ::getSymbolToStringTag,
            attrs { +conf; -enum },
        )
        defineBuiltinGetter("buffer", ::getBuffer, attrs { +conf; -enum })
        defineBuiltinGetter("byteLength", ::getByteLength, attrs { +conf; -enum })
        defineBuiltinGetter("byteOffset", ::getByteOffset, attrs { +conf; -enum })
        defineBuiltinGetter("length", ::getLength, attrs { +conf; -enum })

        defineBuiltin("at", 1, ::at)
        defineBuiltin("copyWithin", 2, ::copyWithin)
        defineBuiltin("entries", 0, ::entries)
        defineBuiltin("every", 1, ::every)
        defineBuiltin("fill", 1, ::fill)
        defineBuiltin("filter", 1, ::filter)
        defineBuiltin("find", 1, ::find)
        defineBuiltin("findIndex", 1, ::findIndex)
        defineBuiltin("forEach", 1, ::forEach)
        defineBuiltin("includes", 1, ::includes)
        defineBuiltin("indexOf", 1, ::indexOf)
        defineBuiltin("join", 1, ::join)
//        defineBuiltin("keys", 0, ::keys)
        defineBuiltin("lastIndexOf", 1, ::lastIndexOf)
//        defineBuiltin("map", 1, ::map)
        defineBuiltin("reduce", 1, ::reduce)
        defineBuiltin("reduceRight", 1, ::reduceRight)
        defineBuiltin("reverse", 0, ::reverse)
//        defineBuiltin("set", 1, ::set)
//        defineBuiltin("slice", 2, ::slice)
        defineBuiltin("some", 1, ::some)
//        defineBuiltin("sort", 1, ::sort)
//        defineBuiltin("subarray", 2, ::subarray)
//        defineBuiltin("toString", 0, ::toString)
//        defineBuiltin("values", 0, ::values)
    }

    companion object {
        // For use with the generic array methods
        private val lengthProducer = { obj: JSObject ->
            getLength(JSArguments(emptyList(), obj)).asLong
        }
        private val indicesProducer = {
            { obj: JSObject ->
                sequence {
                    yieldAll(0L until lengthProducer(obj))
                }
            }
        }

        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSTypedArrayProto(realm).initialize()

        @ECMAImpl("23.2.3.")
        @JvmStatic
        fun getSymbolToStringTag(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (thisValue !is JSObject)
                return JSUndefined
            val kind = thisValue.getSlotOrNull(Slot.TypedArrayKind) ?: return JSUndefined
            return "${kind.name}Array".toValue()
        }

        @ECMAImpl("23.2.3.1")
        @JvmStatic
        fun getBuffer(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            ecmaAssert(thisValue is JSObject && Slot.ViewedArrayBuffer in thisValue)
            return thisValue[Slot.ViewedArrayBuffer]
        }

        @ECMAImpl("23.2.3.2")
        @JvmStatic
        fun getByteLength(arguments: JSArguments): JSValue {
            val buffer = getBuffer(arguments)
            if (AOs.isDetachedBuffer(buffer))
                return JSNumber.ZERO
            return (arguments.thisValue as JSObject)[Slot.ByteLength].toValue()
        }

        @ECMAImpl("23.2.3.3")
        @JvmStatic
        fun getByteOffset(arguments: JSArguments): JSValue {
            val buffer = getBuffer(arguments)
            if (AOs.isDetachedBuffer(buffer))
                return JSNumber.ZERO
            return (arguments.thisValue as JSObject)[Slot.ByteOffset].toValue()
        }

        @ECMAImpl("23.2.3.18")
        @JvmStatic
        fun getLength(arguments: JSArguments): JSValue {
            val buffer = getBuffer(arguments)
            if (AOs.isDetachedBuffer(buffer))
                return JSNumber.ZERO
            return (arguments.thisValue as JSObject)[Slot.ArrayLength].toValue()
        }

        @JvmStatic
        fun at(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            expect(thisValue is JSObject)
            AOs.validateTypedArray(thisValue)
            val len = thisValue[Slot.ArrayLength]
            val relativeIndex = arguments.argument(0).toIntegerOrInfinity()

            val k = if (relativeIndex.isPositiveInfinity || relativeIndex.asLong >= 0) {
                relativeIndex.asLong
            } else {
                len + relativeIndex.asLong
            }

            if (k < 0 || k >= len)
                return JSUndefined

            return thisValue.get(k)
        }

        @ECMAImpl("23.2.3.5")
        @JvmStatic
        fun copyWithin(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            expect(thisValue is JSObject)
            AOs.validateTypedArray(thisValue)

            val (target, start, end) = arguments.takeArgs(0..2)
            val len = thisValue[Slot.ArrayLength]

            val to = AOs.mapWrappedArrayIndex(target.toIntegerOrInfinity(), len.toLong())
            val from = AOs.mapWrappedArrayIndex(start.toIntegerOrInfinity(), len.toLong())
            val relativeEnd = if (end == JSUndefined) len.toValue() else end.toIntegerOrInfinity()
            val final = AOs.mapWrappedArrayIndex(relativeEnd, len.toLong())

            val count = min(final - from, len - to)
            if (count <= 0)
                return thisValue

            val buffer = thisValue[Slot.ViewedArrayBuffer]
            if (AOs.isDetachedBuffer(buffer))
                Errors.TODO("%TypedArray%.prototype.copyWithin").throwTypeError()

            val kind = thisValue[Slot.TypedArrayKind]
            val elementSize = kind.size
            val byteOffset = thisValue[Slot.ByteOffset]
            var toByteIndex = to * elementSize + byteOffset
            var fromByteIndex = from * elementSize + byteOffset
            var countBytes = count * elementSize

            val direction = if (fromByteIndex < toByteIndex && toByteIndex < fromByteIndex + countBytes) {
                fromByteIndex += countBytes - 1
                toByteIndex += countBytes - 1
                -1
            } else 1

            while (countBytes > 0) {
                val value = AOs.getValueFromBuffer(
                    buffer,
                    fromByteIndex.toInt(),
                    AOs.TypedArrayKind.Uint8,
                    true,
                    AOs.TypedArrayOrder.Unordered
                )
                AOs.setValueInBuffer(
                    buffer,
                    toByteIndex.toInt(),
                    AOs.TypedArrayKind.Uint8,
                    value,
                    true,
                    AOs.TypedArrayOrder.Unordered
                )
                fromByteIndex += direction
                toByteIndex += direction
                countBytes--
            }

            return thisValue
        }

        @ECMAImpl("23.2.3.6")
        @JvmStatic
        fun entries(arguments: JSArguments): JSValue {
            AOs.validateTypedArray(arguments.thisValue)
            return AOs.createArrayIterator(arguments.thisValue as JSObject, PropertyKind.KeyValue)
        }

        @ECMAImpl("23.2.3.7")
        @JvmStatic
        fun every(arguments: JSArguments): JSValue {
            AOs.validateTypedArray(arguments.thisValue)
            return JSArrayProto.genericArrayEvery(arguments, lengthProducer, indicesProducer())
        }

        @ECMAImpl("23.2.3.8")
        @JvmStatic
        fun fill(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            AOs.validateTypedArray(thisValue)
            expect(thisValue is JSObject)

            val (valueArg, start, end) = arguments.takeArgs(0..2)
            val len = thisValue[Slot.ArrayLength].toLong()
            val kind = thisValue[Slot.TypedArrayKind]
            val value = if (kind.isBigInt) valueArg.toBigInt() else valueArg.toNumber()

            var k = AOs.mapWrappedArrayIndex(start.toIntegerOrInfinity(), len)
            val relativeEnd = if (end == JSUndefined) len.toValue() else end.toIntegerOrInfinity()
            val final = AOs.mapWrappedArrayIndex(relativeEnd, len)

            if (AOs.isDetachedBuffer(thisValue[Slot.ViewedArrayBuffer]))
                Errors.TODO("%TypedArray%.prototype.fill isDetachedBuffer").throwTypeError()

            while (k < final) {
                AOs.set(thisValue, k.key(), value, true)
                k++
            }

            return thisValue
        }

        @ECMAImpl("23.2.3.9")
        @JvmStatic
        fun filter(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            AOs.validateTypedArray(thisValue)
            expect(thisValue is JSObject)

            val (callbackfn, thisArg) = arguments.takeArgs(0..1)
            if (!callbackfn.isCallable)
                Errors.NotCallable(callbackfn.toString()).throwTypeError()

            val len = thisValue[Slot.ArrayLength]
            val kept = mutableListOf<JSValue>()
            var k = 0

            while (k < len) {
                val value = thisValue.get(k)
                if (AOs.call(callbackfn, thisArg, listOf(value, k.toValue(), thisValue)).toBoolean())
                    kept.add(value)
                k++
            }

            val newArr = AOs.typedArraySpeciesCreate(thisValue, JSArguments(listOf(kept.size.toValue())))
            kept.forEachIndexed { index, value ->
                AOs.set(thisValue, index.key(), value, true)
            }

            return newArr
        }

        @ECMAImpl("23.2.3.10")
        @JvmStatic
        fun find(arguments: JSArguments): JSValue {
            return JSArrayProto.genericArrayFind(arguments, lengthProducer, indicesProducer())
        }

        @ECMAImpl("23.2.3.11")
        @JvmStatic
        fun findIndex(arguments: JSArguments): JSValue {
            return JSArrayProto.genericArrayFindIndex(arguments, lengthProducer, indicesProducer())
        }

        @ECMAImpl("23.2.3.12")
        @JvmStatic
        fun forEach(arguments: JSArguments): JSValue {
            return JSArrayProto.genericArrayForEach(arguments, lengthProducer, indicesProducer())
        }

        @ECMAImpl("23.2.3.13")
        @JvmStatic
        fun includes(arguments: JSArguments): JSValue {
            return JSArrayProto.genericArrayIncludes(arguments, lengthProducer, indicesProducer())
        }

        @ECMAImpl("23.2.3.14")
        @JvmStatic
        fun indexOf(arguments: JSArguments): JSValue {
            return JSArrayProto.genericArrayIndexOf(arguments, lengthProducer, indicesProducer())
        }

        @ECMAImpl("23.2.3.15")
        @JvmStatic
        fun join(arguments: JSArguments): JSValue {
            return JSArrayProto.genericArrayJoin(arguments, lengthProducer)
        }

        @ECMAImpl("23.2.3.17")
        @JvmStatic
        fun lastIndexOf(arguments: JSArguments): JSValue {
            return JSArrayProto.genericArrayLastIndexOf(arguments, lengthProducer, indicesProducer())
        }

        @ECMAImpl("23.2.3.20")
        @JvmStatic
        fun reduce(arguments: JSArguments): JSValue {
            return JSArrayProto.genericArrayReduce(arguments, lengthProducer, indicesProducer(), false)
        }

        @ECMAImpl("23.2.3.21")
        @JvmStatic
        fun reduceRight(arguments: JSArguments): JSValue {
            return JSArrayProto.genericArrayReduce(arguments, lengthProducer, indicesProducer(), true)
        }

        @ECMAImpl("23.2.3.22")
        @JvmStatic
        fun reverse(arguments: JSArguments): JSValue {
            return JSArrayProto.genericArrayReverse(arguments, lengthProducer, indicesProducer())
        }

        @ECMAImpl("23.2.3.26")
        @JvmStatic
        fun some(arguments: JSArguments): JSValue {
            return JSArrayProto.genericArraySome(arguments, lengthProducer, indicesProducer())
        }
    }
}
