package com.reevajs.reeva.runtime.memory

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.toIndex
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.ecmaAssert
import com.reevajs.reeva.utils.key
import com.reevajs.reeva.utils.toValue
import java.util.function.Function

open class JSGenericTypedArrayCtor protected constructor(
    realm: Realm,
    private val kind: AOs.TypedArrayKind,
) : JSNativeFunction(realm, "${kind.name}Array", 3, prototype = realm.typedArrayCtor) {
    override fun init() {
        super.init()

        defineOwnProperty("BYTES_PER_ELEMENT", kind.size.toValue(), 0)
        defineBuiltin("from", 1, JSTypedArrayCtor::from)
        defineBuiltin("of", 0, JSTypedArrayCtor::of)
    }

    @ECMAImpl("22.2.5.1")
    override fun evaluate(arguments: JSArguments): JSValue {
        val newTarget = arguments.newTarget
        if (newTarget == JSUndefined)
            Errors.CtorCallWithoutNew("${kind.name}Array").throwTypeError()
        ecmaAssert(newTarget is JSObject)

        if (arguments.isEmpty())
            return allocateTypedArray(newTarget, kind::getProto, 0)

        val firstArgument = arguments.argument(0)
        if (firstArgument !is JSObject)
            return allocateTypedArray(newTarget, kind::getProto, firstArgument.toIndex())

        val obj = allocateTypedArray(newTarget, kind::getProto)
        when {
            Slot.TypedArrayName in firstArgument ->
                initializeTypedArrayFromTypedArray(obj, firstArgument)
            Slot.ArrayBufferData in firstArgument ->
                initializeTypedArrayFromArrayBuffer(obj, firstArgument, arguments.argument(1), arguments.argument(2))
            else -> {
                val usingIterator = AOs.getMethod(firstArgument, Realm.WellKnownSymbols.iterator)
                if (usingIterator != JSUndefined) {
                    val values = AOs.iterableToList(firstArgument, usingIterator)
                    initializeTypedArrayFromList(obj, values)
                } else {
                    initializeTypedArrayFromArrayLike(obj, firstArgument)
                }
            }
        }

        return obj
    }

    @ECMAImpl("22.2.5.1.1")
    private fun allocateTypedArray(
        newTarget: JSObject,
        defaultProtoProducer: Function<Realm, JSObject>,
        length: Int? = null,
    ): JSValue {
        val proto = AOs.getPrototypeFromConstructor(newTarget, defaultProtoProducer)
        val obj = JSIntegerIndexedObject.create(kind, proto = proto)
        if (length == null) {
            obj[Slot.ByteLength] = 0
            obj[Slot.ByteOffset] = 0
            obj[Slot.ArrayLength] = 0
        } else allocateTypedArrayBuffer(obj, length)

        return obj
    }

    @ECMAImpl("22.2.5.1.2")
    private fun initializeTypedArrayFromTypedArray(obj: JSValue, srcArray: JSValue) {
        ecmaAssert(obj is JSObject && srcArray is JSObject)
        val srcData = srcArray[Slot.ViewedArrayBuffer]
        if (AOs.isDetachedBuffer(srcData))
            Errors.TODO("initializeTypedArrayFromTypedArray isDetachedBuffer 1").throwTypeError()

        val ctorKind = obj[Slot.TypedArrayKind]
        val srcKind = srcArray[Slot.TypedArrayKind]
        val srcByteOffset = srcArray[Slot.ByteOffset]
        val elementLength = srcArray[Slot.ArrayLength]
        val byteLength = ctorKind.size * elementLength

        val bufferCtor = if (AOs.isSharedArrayBuffer(srcData)) {
            AOs.speciesConstructor(srcData, realm.arrayBufferCtor)
        } else realm.arrayBufferCtor

        val data = if (ctorKind == srcKind) {
            AOs.cloneArrayBuffer(srcData, srcByteOffset, byteLength, bufferCtor)
        } else AOs.allocateArrayBuffer(bufferCtor, byteLength).also { buffer ->
            if (AOs.isDetachedBuffer(srcData))
                Errors.TODO("initializeTypedArrayFromTypedArray isDetachedBuffer 2").throwTypeError()
            if (ctorKind.isBigInt != srcKind.isBigInt)
                Errors.TODO("initializeTypedArrayFromTypedArray isBigInt").throwTypeError()

            var srcByteIndex = srcByteOffset
            var targetByteIndex = 0
            var count = elementLength

            while (count > 0) {
                val value = AOs.getValueFromBuffer(
                    srcData,
                    srcByteIndex,
                    srcKind,
                    true,
                    AOs.TypedArrayOrder.Unordered,
                )
                AOs.setValueInBuffer(
                    buffer,
                    targetByteIndex,
                    ctorKind,
                    value,
                    true,
                    AOs.TypedArrayOrder.Unordered,
                )
                srcByteIndex += srcKind.size
                targetByteIndex -= ctorKind.size
                count--
            }
        }

        obj[Slot.ViewedArrayBuffer] = data
        obj[Slot.ByteLength] = byteLength
        obj[Slot.ByteOffset] = 0
        obj[Slot.ArrayLength] = elementLength
    }

    @ECMAImpl("22.2.5.1.3")
    private fun initializeTypedArrayFromArrayBuffer(
        obj: JSValue,
        buffer: JSValue,
        byteOffset: JSValue,
        length: JSValue,
    ) {
        ecmaAssert(obj is JSObject && Slot.TypedArrayKind in obj)
        ecmaAssert(buffer is JSObject && Slot.ArrayBufferData in buffer)

        val ctorKind = obj[Slot.TypedArrayKind]
        val elementSize = ctorKind.size
        val offset = byteOffset.toIndex()
        if (offset % elementSize != 0)
            Errors.TypedArrays.InvalidByteOffset(offset, ctorKind).throwRangeError()

        if (AOs.isDetachedBuffer(buffer))
            Errors.TODO("initializeTypedArrayFromArrayBuffer isDetachedBuffer").throwTypeError()

        val bufferByteLength = buffer[Slot.ArrayBufferByteLength]
        val newByteLength = if (length == JSUndefined) {
            if (bufferByteLength % elementSize != 0)
                Errors.TypedArrays.InvalidBufferLength(bufferByteLength, ctorKind).throwRangeError()
            (bufferByteLength - offset).also {
                if (it < 0) {
                    Errors.TypedArrays.InvalidOffsetAndBufferSize(offset, bufferByteLength, ctorKind)
                        .throwRangeError()
                }
            }
        } else {
            (length.toIndex() * elementSize).also {
                if (offset + it > bufferByteLength) {
                    Errors.TypedArrays.InvalidOffsetAndLength(offset, it, bufferByteLength, ctorKind)
                        .throwRangeError()
                }
            }
        }

        obj[Slot.ViewedArrayBuffer] = buffer
        obj[Slot.ByteLength] = newByteLength
        obj[Slot.ByteOffset] = offset
        obj[Slot.ArrayLength] = newByteLength / elementSize
    }

    @ECMAImpl("22.2.5.1.4")
    private fun initializeTypedArrayFromList(obj: JSValue, values: List<JSValue>) {
        ecmaAssert(obj is JSObject && Slot.TypedArrayName in obj)
        allocateTypedArrayBuffer(obj, values.size)
        values.forEachIndexed { index, value ->
            AOs.set(obj, index.key(), value, true)
        }
    }

    @ECMAImpl("22.2.5.1.5")
    private fun initializeTypedArrayFromArrayLike(obj: JSValue, arrayLike: JSObject) {
        ecmaAssert(obj is JSObject && Slot.TypedArrayName in obj)
        val length = AOs.lengthOfArrayLike(arrayLike)
        for (i in 0 until length)
            AOs.set(obj, i.key(), arrayLike.get(i), true)
    }

    @ECMAImpl("22.2.5.1.6")
    private fun allocateTypedArrayBuffer(obj: JSObject, length: Int) {
        val byteLength = kind.size * length
        val data = AOs.allocateArrayBuffer(realm.arrayBufferCtor, byteLength)
        obj[Slot.ViewedArrayBuffer] = data
        obj[Slot.ByteLength] = byteLength
        obj[Slot.ByteOffset] = 0
        obj[Slot.ArrayLength] = length
    }
}

class JSInt8ArrayCtor(realm: Realm) : JSGenericTypedArrayCtor(realm, AOs.TypedArrayKind.Int8) {
    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSInt8ArrayCtor(realm).initialize()
    }
}

class JSUint8ArrayCtor(realm: Realm) : JSGenericTypedArrayCtor(realm, AOs.TypedArrayKind.Uint8) {
    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSUint8ArrayCtor(realm).initialize()
    }
}

class JSUint8CArrayCtor(realm: Realm) : JSGenericTypedArrayCtor(realm, AOs.TypedArrayKind.Uint8C) {
    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSUint8CArrayCtor(realm).initialize()
    }
}

class JSInt16ArrayCtor(realm: Realm) : JSGenericTypedArrayCtor(realm, AOs.TypedArrayKind.Int16) {
    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSInt16ArrayCtor(realm).initialize()
    }
}

class JSUint16ArrayCtor(realm: Realm) : JSGenericTypedArrayCtor(realm, AOs.TypedArrayKind.Uint16) {
    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSUint16ArrayCtor(realm).initialize()
    }
}

class JSInt32ArrayCtor(realm: Realm) : JSGenericTypedArrayCtor(realm, AOs.TypedArrayKind.Int32) {
    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSInt32ArrayCtor(realm).initialize()
    }
}

class JSUint32ArrayCtor(realm: Realm) : JSGenericTypedArrayCtor(realm, AOs.TypedArrayKind.Uint32) {
    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSUint32ArrayCtor(realm).initialize()
    }
}

class JSFloat32ArrayCtor(realm: Realm) : JSGenericTypedArrayCtor(realm, AOs.TypedArrayKind.Float32) {
    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSFloat32ArrayCtor(realm).initialize()
    }
}

class JSFloat64ArrayCtor(realm: Realm) : JSGenericTypedArrayCtor(realm, AOs.TypedArrayKind.Float64) {
    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSFloat64ArrayCtor(realm).initialize()
    }
}

class JSBigInt64ArrayCtor(realm: Realm) : JSGenericTypedArrayCtor(realm, AOs.TypedArrayKind.BigInt64) {
    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSBigInt64ArrayCtor(realm).initialize()
    }
}

class JSBigUint64ArrayCtor(realm: Realm) : JSGenericTypedArrayCtor(realm, AOs.TypedArrayKind.BigUint64) {
    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSBigUint64ArrayCtor(realm).initialize()
    }
}
