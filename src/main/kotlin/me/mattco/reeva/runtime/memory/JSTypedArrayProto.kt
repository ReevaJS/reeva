package me.mattco.reeva.runtime.memory

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.*
import me.mattco.reeva.runtime.arrays.JSArrayProto
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSNumber
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.*
import kotlin.math.min

class JSTypedArrayProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    // For use with the generic array methods
    private val lengthProducer = { realm: Realm, obj: JSObject -> getLength(realm, obj).asLong }
    private val indicesProducer = { obj: JSObject ->
        sequence {
            yieldAll(0L until lengthProducer(realm, obj))
        }
    }

    override fun init() {
        super.init()

        defineNativeAccessor(Realm.`@@toStringTag`.key(), attrs { +conf -enum }, ::`get@@toStringTag`)
        defineNativeAccessor("buffer", attrs { +conf - enum }, ::getBuffer)
        defineNativeAccessor("byteLength", attrs { +conf - enum }, ::getByteLength)
        defineNativeAccessor("byteOffset", attrs { +conf - enum }, ::getByteOffset)
        defineNativeAccessor("length", attrs { +conf - enum }, ::getLength)

        defineNativeFunction("at", 1, ::at)
        defineNativeFunction("copyWithin", 2, ::copyWithin)
        defineNativeFunction("entries", 0, ::entries)
        defineNativeFunction("every", 1, ::every)
        defineNativeFunction("fill", 1, ::fill)
        defineNativeFunction("filter", 1, ::filter)
        defineNativeFunction("find", 1, ::find)
        defineNativeFunction("findIndex", 1, ::findIndex)
        defineNativeFunction("forEach", 1, ::forEach)
        defineNativeFunction("includes", 1, ::includes)
//        defineNativeFunction("indexOf", 1, ::indexOf)
        defineNativeFunction("join", 1, ::join)
//        defineNativeFunction("keys", 0, ::keys)
        defineNativeFunction("lastIndexOf", 1, ::lastIndexOf)
//        defineNativeFunction("map", 1, ::map)
        defineNativeFunction("reduce", 1, ::reduce)
        defineNativeFunction("reduceRight", 1, ::reduceRight)
        defineNativeFunction("reverse", 0, ::reverse)
//        defineNativeFunction("set", 1, ::set)
//        defineNativeFunction("slice", 2, ::slice)
        defineNativeFunction("some", 1, ::some)
//        defineNativeFunction("sort", 1, ::sort)
//        defineNativeFunction("subarray", 2, ::subarray)
//        defineNativeFunction("toString", 0, ::toString)
//        defineNativeFunction("values", 0, ::values)
    }

    private fun `get@@toStringTag`(realm: Realm, thisValue: JSValue): JSValue {
        if (thisValue !is JSObject)
            return JSUndefined
        val kind = thisValue.getSlotAs<Operations.TypedArrayKind?>(SlotName.TypedArrayKind) ?: return JSUndefined
        return "${kind.name}Array".toValue()
    }

    private fun getBuffer(realm: Realm, thisValue: JSValue): JSObject {
        ecmaAssert(thisValue is JSObject && thisValue.hasSlot(SlotName.ViewedArrayBuffer))
        return thisValue.getSlotAs(SlotName.ViewedArrayBuffer)
    }

    private fun getByteLength(realm: Realm, thisValue: JSValue): JSValue {
        val buffer = getBuffer(realm, thisValue)
        if (Operations.isDetachedBuffer(buffer))
            return JSNumber.ZERO
        return (thisValue as JSObject).getSlotAs<Int>(SlotName.ByteLength).toValue()
    }

    private fun getByteOffset(realm: Realm, thisValue: JSValue): JSValue {
        val buffer = getBuffer(realm, thisValue)
        if (Operations.isDetachedBuffer(buffer))
            return JSNumber.ZERO
        return (thisValue as JSObject).getSlotAs<Int>(SlotName.ByteOffset).toValue()
    }

    private fun getLength(realm: Realm, thisValue: JSValue): JSValue {
        val buffer = getBuffer(realm, thisValue)
        if (Operations.isDetachedBuffer(buffer))
            return JSNumber.ZERO
        return (thisValue as JSObject).getSlotAs<Int>(SlotName.ArrayLength).toValue()
    }

    private fun at(realm: Realm, arguments: JSArguments): JSValue {
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

    private fun copyWithin(realm: Realm, arguments: JSArguments): JSValue {
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

    private fun entries(realm: Realm, arguments: JSArguments): JSValue {
        Operations.validateTypedArray(realm, arguments.thisValue)
        return Operations.createArrayIterator(realm, this, PropertyKind.KeyValue)
    }

    private fun every(realm: Realm, arguments: JSArguments): JSValue {
        Operations.validateTypedArray(realm, arguments.thisValue)
        return JSArrayProto.genericArrayEvery(realm, arguments, lengthProducer, indicesProducer)
    }

    private fun fill(realm: Realm, arguments: JSArguments): JSValue {
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

    private fun filter(realm: Realm, arguments: JSArguments): JSValue {
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

    private fun find(realm: Realm, arguments: JSArguments): JSValue {
        return JSArrayProto.genericArrayFind(realm, arguments, lengthProducer, indicesProducer)
    }

    private fun findIndex(realm: Realm, arguments: JSArguments): JSValue {
        return JSArrayProto.genericArrayFindIndex(realm, arguments, lengthProducer, indicesProducer)
    }

    private fun forEach(realm: Realm, arguments: JSArguments): JSValue {
        return JSArrayProto.genericArrayForEach(realm, arguments, lengthProducer, indicesProducer)
    }

    private fun includes(realm: Realm, arguments: JSArguments): JSValue {
        return JSArrayProto.genericArrayIncludes(realm, arguments, lengthProducer, indicesProducer)
    }

//    private fun indexOf(realm: Realm, arguments: JSArguments): JSValue {
//        return JSArrayProto.genericArrayIndexOf(thisValue, arguments, lengthProducer, indicesProducer)
//    }

    private fun join(realm: Realm, arguments: JSArguments): JSValue {
        return JSArrayProto.genericArrayJoin(realm, arguments, lengthProducer)
    }

    private fun lastIndexOf(realm: Realm, arguments: JSArguments): JSValue {
        return JSArrayProto.genericArrayLastIndexOf(realm, arguments, lengthProducer, indicesProducer)
    }

    private fun reduce(realm: Realm, arguments: JSArguments): JSValue {
        return JSArrayProto.genericArrayReduce(realm, arguments, lengthProducer, indicesProducer, false)
    }

    private fun reduceRight(realm: Realm, arguments: JSArguments): JSValue {
        return JSArrayProto.genericArrayReduce(realm, arguments, lengthProducer, indicesProducer, true)
    }

    private fun reverse(realm: Realm, arguments: JSArguments): JSValue {
        return JSArrayProto.genericArrayReverse(realm, arguments, lengthProducer, indicesProducer)
    }

    private fun some(realm: Realm, arguments: JSArguments): JSValue {
        return JSArrayProto.genericArraySome(realm, arguments, lengthProducer, indicesProducer)
    }

    companion object {
        fun create(realm: Realm) = JSTypedArrayProto(realm).initialize()
    }
}
