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
    private val lengthProducer = { obj: JSObject -> getLength(obj).asLong }
    private val indicesProducer = { obj: JSObject -> 0L..lengthProducer(obj) }

    override fun init() {
        super.init()

        defineNativeAccessor(Realm.`@@toStringTag`.key(), attrs { +conf -enum }, ::`get@@toStringTag`)
        defineNativeAccessor("buffer", attrs { +conf -enum }, ::getBuffer)
        defineNativeAccessor("byteLength", attrs { +conf -enum }, ::getByteLength)
        defineNativeAccessor("byteOffset", attrs { +conf -enum }, ::getByteOffset)
        defineNativeAccessor("length", attrs { +conf -enum }, ::getLength)
    }

    private fun `get@@toStringTag`(thisValue: JSValue): JSValue {
        if (thisValue !is JSObject)
            return JSUndefined
        val kind = thisValue.getSlotAs<Operations.TypedArrayKind?>(SlotName.TypedArrayKind) ?: return JSUndefined
        return "${kind.name}Array".toValue()
    }

    private fun getBuffer(thisValue: JSValue): JSObject {
        ecmaAssert(thisValue is JSObject && thisValue.hasSlot(SlotName.ViewedArrayBuffer))
        return thisValue.getSlotAs(SlotName.ViewedArrayBuffer)
    }

    private fun getByteLength(thisValue: JSValue): JSValue {
        val buffer = getBuffer(thisValue)
        if (Operations.isDetachedBuffer(buffer))
            return JSNumber.ZERO
        return (thisValue as JSObject).getSlotAs<Int>(SlotName.ByteLength).toValue()
    }

    private fun getByteOffset(thisValue: JSValue): JSValue {
        val buffer = getBuffer(thisValue)
        if (Operations.isDetachedBuffer(buffer))
            return JSNumber.ZERO
        return (thisValue as JSObject).getSlotAs<Int>(SlotName.ByteOffset).toValue()
    }

    private fun getLength(thisValue: JSValue): JSValue {
        val buffer = getBuffer(thisValue)
        if (Operations.isDetachedBuffer(buffer))
            return JSNumber.ZERO
        return (thisValue as JSObject).getSlotAs<Int>(SlotName.ArrayLength).toValue()
    }

    private fun copyWithin(arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        val buffer = Operations.validateTypedArray(thisValue)
        expect(thisValue is JSObject)

        val (target, start, end) = arguments.takeArgs(0..2)
        val len = thisValue.getSlotAs<Int>(SlotName.ArrayLength)

        val to = Operations.mapWrappedArrayIndex(target.toIntegerOrInfinity(), len.toLong())
        val from = Operations.mapWrappedArrayIndex(start.toIntegerOrInfinity(), len.toLong())
        val relativeEnd = if (end == JSUndefined) len.toValue() else end.toIntegerOrInfinity()
        val final = Operations.mapWrappedArrayIndex(relativeEnd, len.toLong())

        val count = min(final - from, len - to)
        if (count <= 0)
            return thisValue

        if (Operations.isDetachedBuffer(buffer))
            Errors.TODO("%TypedArray%.prototype.copyWithin").throwTypeError()

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
            val value = Operations.getValueFromBuffer(buffer, fromByteIndex.toInt(), Operations.TypedArrayKind.Uint8, true, Operations.TypedArrayOrder.Unordered)
            Operations.setValueInBuffer(buffer, toByteIndex.toInt(), Operations.TypedArrayKind.Uint8, value, true, Operations.TypedArrayOrder.Unordered)
            fromByteIndex += direction
            toByteIndex += direction
            countBytes--
        }

        return thisValue
    }

    private fun entries(arguments: JSArguments): JSValue {
        Operations.validateTypedArray(arguments.thisValue)
        return Operations.createArrayIterator(realm, this, PropertyKind.KeyValue)
    }

    private fun every(arguments: JSArguments): JSValue {
        return JSArrayProto.genericArrayEvery(arguments, lengthProducer, indicesProducer)
    }

    private fun fill(arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        val buffer = Operations.validateTypedArray(thisValue)
        expect(thisValue is JSObject)

        if (Operations.isDetachedBuffer(buffer))
            Errors.TODO("%TypedArray%.prototype.fill isDetachedBuffer")

        val (valueArg, start, end) = arguments.takeArgs(0..2)
        val len = thisValue.getSlotAs<Int>(SlotName.ArrayLength).toLong()
        val kind = thisValue.getSlotAs<Operations.TypedArrayKind>(SlotName.TypedArrayKind)
        val value = if (kind.isBigInt) valueArg.toBigInt() else valueArg.toNumber()

        var k = Operations.mapWrappedArrayIndex(start.toIntegerOrInfinity(), len)
        val relativeEnd = if (end == JSUndefined) len.toValue() else end.toIntegerOrInfinity()
        val final = Operations.mapWrappedArrayIndex(relativeEnd, len)

        while (k < final)
            Operations.set(thisValue, k.key(), value, true)

        return thisValue
    }

    private fun filter(arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        Operations.validateTypedArray(thisValue)
        expect(thisValue is JSObject)

        val (callbackfn, thisArg) = arguments.takeArgs(0..1)
        if (!callbackfn.isCallable)
            Errors.NotCallable(callbackfn.toPrintableString()).throwTypeError()

        val len = thisValue.getSlotAs<Int>(SlotName.ArrayLength)
        val kept = mutableListOf<JSValue>()
        var k = 0

        while (k < len) {
            val value = thisValue.get(k)
            if (Operations.call(callbackfn, thisArg, listOf(value, k.toValue(), thisValue)).toBoolean())
                kept.add(value)
            k++
        }

        val newArr = Operations.typedArraySpeciesCreate(thisValue, JSArguments(listOf(kept.size.toValue())))
        kept.forEachIndexed { index, value ->
            Operations.set(thisValue, index.key(), value, true)
        }

        return newArr
    }

    private fun find(arguments: JSArguments): JSValue {
        return JSArrayProto.genericArrayFind(arguments, lengthProducer, indicesProducer)
    }

    private fun findIndex(arguments: JSArguments): JSValue {
        return JSArrayProto.genericArrayFindIndex(arguments, lengthProducer, indicesProducer)
    }

    private fun forEach(arguments: JSArguments): JSValue {
        return JSArrayProto.genericArrayForEach(arguments, lengthProducer, indicesProducer)
    }

    private fun includes(arguments: JSArguments): JSValue {
        return JSArrayProto.genericArrayIncludes(arguments, lengthProducer, indicesProducer)
    }

//    private fun indexOf(arguments: JSArguments): JSValue {
//        return JSArrayProto.genericArrayIndexOf(thisValue, arguments, lengthProducer, indicesProducer)
//    }

    private fun join(arguments: JSArguments): JSValue {
        return JSArrayProto.genericArrayJoin(arguments, lengthProducer)
    }

    private fun lastIndexOf(arguments: JSArguments): JSValue {
        return JSArrayProto.genericArrayLastIndexOf(arguments, lengthProducer, indicesProducer)
    }

    private fun reduce(arguments: JSArguments): JSValue {
        return JSArrayProto.genericArrayReduce(arguments, lengthProducer, indicesProducer, false)
    }

    private fun reduceRight(arguments: JSArguments): JSValue {
        return JSArrayProto.genericArrayReduce(arguments, lengthProducer, indicesProducer, true)
    }

    private fun reverse(arguments: JSArguments): JSValue {
        return JSArrayProto.genericArrayReverse(arguments, lengthProducer, indicesProducer)
    }

    private fun some(arguments: JSArguments): JSValue {
        return JSArrayProto.genericArraySome(arguments, lengthProducer, indicesProducer)
    }

    companion object {
        fun create(realm: Realm) = JSTypedArrayProto(realm).initialize()
    }
}
