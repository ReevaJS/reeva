package com.reevajs.reeva.runtime.memory

import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.arrays.JSArrayProto
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSNumber
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.*
import kotlin.math.min

class JSTypedArrayProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineBuiltinGetter(
            Realm.WellKnownSymbols.toStringTag,
            ReevaBuiltin.TypedArrayProtoGetSymbolToStringTag,
            attrs { +conf; -enum },
        )
        defineBuiltinGetter("buffer", ReevaBuiltin.TypedArrayProtoGetBuffer, attrs { +conf; -enum })
        defineBuiltinGetter("byteLength", ReevaBuiltin.TypedArrayProtoGetByteLength, attrs { +conf; -enum })
        defineBuiltinGetter("byteOffset", ReevaBuiltin.TypedArrayProtoGetByteOffset, attrs { +conf; -enum })
        defineBuiltinGetter("length", ReevaBuiltin.TypedArrayProtoGetLength, attrs { +conf; -enum })

        defineBuiltin("at", 1, ReevaBuiltin.TypedArrayProtoAt)
        defineBuiltin("copyWithin", 2, ReevaBuiltin.TypedArrayProtoCopyWithin)
        defineBuiltin("entries", 0, ReevaBuiltin.TypedArrayProtoEntries)
        defineBuiltin("every", 1, ReevaBuiltin.TypedArrayProtoEvery)
        defineBuiltin("fill", 1, ReevaBuiltin.TypedArrayProtoFill)
        defineBuiltin("filter", 1, ReevaBuiltin.TypedArrayProtoFilter)
        defineBuiltin("find", 1, ReevaBuiltin.TypedArrayProtoFind)
        defineBuiltin("findIndex", 1, ReevaBuiltin.TypedArrayProtoFindIndex)
        defineBuiltin("forEach", 1, ReevaBuiltin.TypedArrayProtoForEach)
        defineBuiltin("includes", 1, ReevaBuiltin.TypedArrayProtoIncludes)
        defineBuiltin("indexOf", 1, ReevaBuiltin.TypedArrayProtoIndexOf)
        defineBuiltin("join", 1, ReevaBuiltin.TypedArrayProtoJoin)
//        defineBuiltin("keys", 0, Builtin.TypedArrayProtoKeys)
        defineBuiltin("lastIndexOf", 1, ReevaBuiltin.TypedArrayProtoLastIndexOf)
//        defineBuiltin("map", 1, Builtin.TypedArrayProtoMap)
        defineBuiltin("reduce", 1, ReevaBuiltin.TypedArrayProtoReduce)
        defineBuiltin("reduceRight", 1, ReevaBuiltin.TypedArrayProtoReduceRight)
        defineBuiltin("reverse", 0, ReevaBuiltin.TypedArrayProtoReverse)
//        defineBuiltin("set", 1, Builtin.TypedArrayProtoSet)
//        defineBuiltin("slice", 2, Builtin.TypedArrayProtoSlice)
        defineBuiltin("some", 1, ReevaBuiltin.TypedArrayProtoSome)
//        defineBuiltin("sort", 1, Builtin.TypedArrayProtoSort)
//        defineBuiltin("subarray", 2, Builtin.TypedArrayProtoSubarray)
//        defineBuiltin("toString", 0, Builtin.TypedArrayProtoToString)
//        defineBuiltin("values", 0, Builtin.TypedArrayProtoValues)
    }

    companion object {
        // For use with the generic array methods
        private val lengthProducer = { realm: Realm, obj: JSObject ->
            getLength(realm, JSArguments(emptyList(), obj)).asLong
        }
        private val indicesProducer = { realm: Realm ->
            { obj: JSObject ->
                sequence {
                    yieldAll(0L until lengthProducer(realm, obj))
                }
            }
        }

        fun create(realm: Realm) = JSTypedArrayProto(realm).initialize()

        @ECMAImpl("23.2.3.")
        @JvmStatic
        fun getSymbolToStringTag(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (thisValue !is JSObject)
                return JSUndefined
            val kind = thisValue.getSlotAs<Operations.TypedArrayKind?>(SlotName.TypedArrayKind) ?: return JSUndefined
            return "${kind.name}Array".toValue()
        }

        @ECMAImpl("23.2.3.1")
        @JvmStatic
        fun getBuffer(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            ecmaAssert(thisValue is JSObject && thisValue.hasSlot(SlotName.ViewedArrayBuffer))
            return thisValue.getSlotAs(SlotName.ViewedArrayBuffer)
        }

        @ECMAImpl("23.2.3.2")
        @JvmStatic
        fun getByteLength(realm: Realm, arguments: JSArguments): JSValue {
            val buffer = getBuffer(realm, arguments)
            if (Operations.isDetachedBuffer(buffer))
                return JSNumber.ZERO
            return (arguments.thisValue as JSObject).getSlotAs<Int>(SlotName.ByteLength).toValue()
        }

        @ECMAImpl("23.2.3.3")
        @JvmStatic
        fun getByteOffset(realm: Realm, arguments: JSArguments): JSValue {
            val buffer = getBuffer(realm, arguments)
            if (Operations.isDetachedBuffer(buffer))
                return JSNumber.ZERO
            return (arguments.thisValue as JSObject).getSlotAs<Int>(SlotName.ByteOffset).toValue()
        }

        @ECMAImpl("23.2.3.18")
        @JvmStatic
        fun getLength(realm: Realm, arguments: JSArguments): JSValue {
            val buffer = getBuffer(realm, arguments)
            if (Operations.isDetachedBuffer(buffer))
                return JSNumber.ZERO
            return (arguments.thisValue as JSObject).getSlotAs<Int>(SlotName.ArrayLength).toValue()
        }

        @JvmStatic
        fun at(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            expect(thisValue is JSObject)
            Operations.validateTypedArray(realm, thisValue)
            val len = thisValue.getSlotAs<Int>(SlotName.ArrayLength)
            val relativeIndex = Operations.toIntegerOrInfinity(realm, arguments.argument(0))

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
        fun copyWithin(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            expect(thisValue is JSObject)
            Operations.validateTypedArray(realm, thisValue)

            val (target, start, end) = arguments.takeArgs(0..2)
            val len = thisValue.getSlotAs<Int>(SlotName.ArrayLength)

            val to = Operations.mapWrappedArrayIndex(target.toIntegerOrInfinity(realm), len.toLong())
            val from = Operations.mapWrappedArrayIndex(start.toIntegerOrInfinity(realm), len.toLong())
            val relativeEnd = if (end == JSUndefined) len.toValue() else end.toIntegerOrInfinity(realm)
            val final = Operations.mapWrappedArrayIndex(relativeEnd, len.toLong())

            val count = min(final - from, len - to)
            if (count <= 0)
                return thisValue

            val buffer = thisValue.getSlotAs<JSValue>(SlotName.ViewedArrayBuffer)
            if (Operations.isDetachedBuffer(buffer))
                Errors.TODO("%TypedArray%.prototype.copyWithin").throwTypeError(realm)

            val kind = thisValue.getSlotAs<Operations.TypedArrayKind>(SlotName.TypedArrayKind)
            val elementSize = kind.size
            val byteOffset = thisValue.getSlotAs<Int>(SlotName.ByteOffset)
            var toByteIndex = to * elementSize + byteOffset
            var fromByteIndex = from * elementSize + byteOffset
            var countBytes = count * elementSize

            val direction = if (fromByteIndex < toByteIndex && toByteIndex < fromByteIndex + countBytes) {
                fromByteIndex += countBytes - 1
                toByteIndex += countBytes - 1
                -1
            } else 1

            while (countBytes > 0) {
                val value = Operations.getValueFromBuffer(
                    buffer,
                    fromByteIndex.toInt(),
                    Operations.TypedArrayKind.Uint8,
                    true,
                    Operations.TypedArrayOrder.Unordered
                )
                Operations.setValueInBuffer(
                    realm,
                    buffer,
                    toByteIndex.toInt(),
                    Operations.TypedArrayKind.Uint8,
                    value,
                    true,
                    Operations.TypedArrayOrder.Unordered
                )
                fromByteIndex += direction
                toByteIndex += direction
                countBytes--
            }

            return thisValue
        }

        @ECMAImpl("23.2.3.6")
        @JvmStatic
        fun entries(realm: Realm, arguments: JSArguments): JSValue {
            Operations.validateTypedArray(realm, arguments.thisValue)
            return Operations.createArrayIterator(realm, arguments.thisValue as JSObject, PropertyKind.KeyValue)
        }

        @ECMAImpl("23.2.3.7")
        @JvmStatic
        fun every(realm: Realm, arguments: JSArguments): JSValue {
            Operations.validateTypedArray(realm, arguments.thisValue)
            return JSArrayProto.genericArrayEvery(realm, arguments, lengthProducer, indicesProducer(realm))
        }

        @ECMAImpl("23.2.3.8")
        @JvmStatic
        fun fill(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            Operations.validateTypedArray(realm, thisValue)
            expect(thisValue is JSObject)

            val (valueArg, start, end) = arguments.takeArgs(0..2)
            val len = thisValue.getSlotAs<Int>(SlotName.ArrayLength).toLong()
            val kind = thisValue.getSlotAs<Operations.TypedArrayKind>(SlotName.TypedArrayKind)
            val value = if (kind.isBigInt) valueArg.toBigInt(realm) else valueArg.toNumber(realm)

            var k = Operations.mapWrappedArrayIndex(start.toIntegerOrInfinity(realm), len)
            val relativeEnd = if (end == JSUndefined) len.toValue() else end.toIntegerOrInfinity(realm)
            val final = Operations.mapWrappedArrayIndex(relativeEnd, len)

            if (Operations.isDetachedBuffer(thisValue.getSlotAs(SlotName.ViewedArrayBuffer)))
                Errors.TODO("%TypedArray%.prototype.fill isDetachedBuffer").throwTypeError(realm)

            while (k < final) {
                Operations.set(realm, thisValue, k.key(), value, true)
                k++
            }

            return thisValue
        }

        @ECMAImpl("23.2.3.9")
        @JvmStatic
        fun filter(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            Operations.validateTypedArray(realm, thisValue)
            expect(thisValue is JSObject)

            val (callbackfn, thisArg) = arguments.takeArgs(0..1)
            if (!callbackfn.isCallable)
                Errors.NotCallable(callbackfn.toPrintableString()).throwTypeError(realm)

            val len = thisValue.getSlotAs<Int>(SlotName.ArrayLength)
            val kept = mutableListOf<JSValue>()
            var k = 0

            while (k < len) {
                val value = thisValue.get(k)
                if (Operations.call(realm, callbackfn, thisArg, listOf(value, k.toValue(), thisValue)).toBoolean())
                    kept.add(value)
                k++
            }

            val newArr = Operations.typedArraySpeciesCreate(realm, thisValue, JSArguments(listOf(kept.size.toValue())))
            kept.forEachIndexed { index, value ->
                Operations.set(realm, thisValue, index.key(), value, true)
            }

            return newArr
        }

        @ECMAImpl("23.2.3.10")
        @JvmStatic
        fun find(realm: Realm, arguments: JSArguments): JSValue {
            return JSArrayProto.genericArrayFind(realm, arguments, lengthProducer, indicesProducer(realm))
        }

        @ECMAImpl("23.2.3.11")
        @JvmStatic
        fun findIndex(realm: Realm, arguments: JSArguments): JSValue {
            return JSArrayProto.genericArrayFindIndex(realm, arguments, lengthProducer, indicesProducer(realm))
        }

        @ECMAImpl("23.2.3.12")
        @JvmStatic
        fun forEach(realm: Realm, arguments: JSArguments): JSValue {
            return JSArrayProto.genericArrayForEach(realm, arguments, lengthProducer, indicesProducer(realm))
        }

        @ECMAImpl("23.2.3.13")
        @JvmStatic
        fun includes(realm: Realm, arguments: JSArguments): JSValue {
            return JSArrayProto.genericArrayIncludes(realm, arguments, lengthProducer, indicesProducer(realm))
        }

        @ECMAImpl("23.2.3.14")
        @JvmStatic
        fun indexOf(realm: Realm, arguments: JSArguments): JSValue {
            return JSArrayProto.genericArrayIndexOf(realm, arguments, lengthProducer, indicesProducer(realm))
        }

        @ECMAImpl("23.2.3.15")
        @JvmStatic
        fun join(realm: Realm, arguments: JSArguments): JSValue {
            return JSArrayProto.genericArrayJoin(realm, arguments, lengthProducer)
        }

        @ECMAImpl("23.2.3.17")
        @JvmStatic
        fun lastIndexOf(realm: Realm, arguments: JSArguments): JSValue {
            return JSArrayProto.genericArrayLastIndexOf(realm, arguments, lengthProducer, indicesProducer(realm))
        }

        @ECMAImpl("23.2.3.20")
        @JvmStatic
        fun reduce(realm: Realm, arguments: JSArguments): JSValue {
            return JSArrayProto.genericArrayReduce(realm, arguments, lengthProducer, indicesProducer(realm), false)
        }

        @ECMAImpl("23.2.3.21")
        @JvmStatic
        fun reduceRight(realm: Realm, arguments: JSArguments): JSValue {
            return JSArrayProto.genericArrayReduce(realm, arguments, lengthProducer, indicesProducer(realm), true)
        }

        @ECMAImpl("23.2.3.22")
        @JvmStatic
        fun reverse(realm: Realm, arguments: JSArguments): JSValue {
            return JSArrayProto.genericArrayReverse(realm, arguments, lengthProducer, indicesProducer(realm))
        }

        @ECMAImpl("23.2.3.26")
        @JvmStatic
        fun some(realm: Realm, arguments: JSArguments): JSValue {
            return JSArrayProto.genericArraySome(realm, arguments, lengthProducer, indicesProducer(realm))
        }
    }
}
