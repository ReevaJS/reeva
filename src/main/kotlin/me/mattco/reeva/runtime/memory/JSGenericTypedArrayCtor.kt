package me.mattco.reeva.runtime.memory

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.SlotName
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.runtime.toIndex
import me.mattco.reeva.utils.*

open class JSGenericTypedArrayCtor protected constructor(
    realm: Realm,
    private val kind: Operations.TypedArrayKind,
) : JSNativeFunction(realm, "${kind.name}Array", 3, prototype = realm.typedArrayCtor) {
    init {
        isConstructable = true
    }

    override fun init() {
        super.init()

        defineOwnProperty("BYTES_PER_ELEMENT", kind.size.toValue(), 0)
        realm.typedArrayCtor.inject(this)
    }

    @ECMAImpl("22.2.5.1")
    override fun evaluate(arguments: JSArguments): JSValue {
        val newTarget = super.newTarget
        if (newTarget == JSUndefined)
            Errors.CtorCallWithoutNew("${kind.name}Array").throwTypeError()
        ecmaAssert(newTarget is JSObject)

        val proto = kind.getProto(realm)
        if (arguments.isEmpty())
            return allocateTypedArray(newTarget, proto, 0)

        val firstArgument = arguments.argument(0)
        if (firstArgument !is JSObject)
            return allocateTypedArray(newTarget, proto, firstArgument.toIndex())

        val obj = allocateTypedArray(newTarget, proto)
        when {
            firstArgument.hasSlot(SlotName.TypedArrayName) ->
                initializeTypedArrayFromTypedArray(obj, firstArgument)
            firstArgument.hasSlot(SlotName.ArrayBufferData) ->
                initializeTypedArrayFromArrayBuffer(obj, firstArgument, arguments.argument(1), arguments.argument(2))
            else -> {
                val usingIterator = Operations.getMethod(firstArgument, Realm.`@@iterator`)
                if (usingIterator != JSUndefined) {
                    val values = Operations.iterableToList(firstArgument, usingIterator)
                    initializeTypedArrayFromList(obj, values)
                } else {
                    initializeTypedArrayFromArrayLike(obj, firstArgument)
                }
            }
        }

        return obj
    }

    @ECMAImpl("22.2.5.1.1")
    private fun allocateTypedArray(newTarget: JSObject, defaultProto: JSObject, length: Int? = null): JSValue {
        val proto = Operations.getPrototypeFromConstructor(newTarget, defaultProto)
        val obj = JSIntegerIndexedObject.create(realm, kind, proto)
        if (length == null) {
            obj.setSlot(SlotName.ByteLength, 0)
            obj.setSlot(SlotName.ByteOffset, 0)
            obj.setSlot(SlotName.ArrayLength, 0)
        } else allocateTypedArrayBuffer(obj, length)

        return obj
    }

    @ECMAImpl("22.2.5.1.2")
    private fun initializeTypedArrayFromTypedArray(obj: JSValue, srcArray: JSValue) {
        ecmaAssert(obj is JSObject && srcArray is JSObject)
        val srcData = srcArray.getSlotAs<JSValue>(SlotName.ViewedArrayBuffer)
        if (Operations.isDetachedBuffer(srcData))
            Errors.TODO("initializeTypedArrayFromTypedArray isDetachedBuffer 1").throwTypeError()

        val ctorKind = obj.getSlotAs<Operations.TypedArrayKind>(SlotName.TypedArrayKind)
        val srcKind = srcArray.getSlotAs<Operations.TypedArrayKind>(SlotName.TypedArrayKind)
        val srcByteOffset = srcArray.getSlotAs<Int>(SlotName.ByteOffset)
        val elementLength = srcArray.getSlotAs<Int>(SlotName.ArrayLength)
        val byteLength = ctorKind.size * elementLength

        val bufferCtor = if (Operations.isSharedArrayBuffer(srcData)) {
            Operations.speciesConstructor(srcData, realm.arrayBufferCtor)
        } else realm.arrayBufferCtor

        val data = if (ctorKind == srcKind) {
            Operations.cloneArrayBuffer(realm, srcData, srcByteOffset, byteLength, bufferCtor)
        } else Operations.allocateArrayBuffer(realm, bufferCtor, byteLength).also { buffer ->
            if (Operations.isDetachedBuffer(srcData))
                Errors.TODO("initializeTypedArrayFromTypedArray isDetachedBuffer 2").throwTypeError()
            if (ctorKind.isBigInt != srcKind.isBigInt)
                Errors.TODO("initializeTypedArrayFromTypedArray isBigInt").throwTypeError()

            var srcByteIndex = srcByteOffset
            var targetByteIndex = 0
            var count = elementLength

            while (count > 0) {
                val value = Operations.getValueFromBuffer(srcData, srcByteIndex, srcKind, true, Operations.TypedArrayOrder.Unordered)
                Operations.setValueInBuffer(buffer, targetByteIndex, ctorKind, value, true, Operations.TypedArrayOrder.Unordered)
                srcByteIndex += srcKind.size
                targetByteIndex -= ctorKind.size
                count--
            }
        }

        obj.setSlot(SlotName.ViewedArrayBuffer, data)
        obj.setSlot(SlotName.ByteLength, byteLength)
        obj.setSlot(SlotName.ByteOffset, 0)
        obj.setSlot(SlotName.ArrayLength, elementLength)
    }

    @ECMAImpl("22.2.5.1.3")
    private fun initializeTypedArrayFromArrayBuffer(obj: JSValue, buffer: JSValue, byteOffset: JSValue, length: JSValue) {
        ecmaAssert(obj is JSObject && obj.hasSlot(SlotName.TypedArrayKind))
        ecmaAssert(buffer is JSObject && buffer.hasSlot(SlotName.ArrayBufferData))

        val ctorKind = obj.getSlotAs<Operations.TypedArrayKind>(SlotName.TypedArrayKind)
        val elementSize = ctorKind.size
        val offset = byteOffset.toIndex()
        if (offset % elementSize != 0)
            Errors.TypedArrays.InvalidByteOffset(offset, ctorKind).throwRangeError()

        if (Operations.isDetachedBuffer(buffer))
            Errors.TODO("initializeTypedArrayFromArrayBuffer isDetachedBuffer").throwTypeError()

        val bufferByteLength = buffer.getSlotAs<Int>(SlotName.ArrayBufferByteLength)
        val newByteLength = if (length == JSUndefined) {
            if (bufferByteLength % elementSize != 0)
                Errors.TypedArrays.InvalidBufferLength(bufferByteLength, ctorKind).throwRangeError()
            (bufferByteLength - offset).also {
                if (it < 0)
                    Errors.TypedArrays.InvalidOffsetAndBufferSize(offset, bufferByteLength, ctorKind).throwRangeError()
            }
        } else {
            (length.toIndex() * elementSize).also {
                if (offset + it > bufferByteLength)
                    Errors.TypedArrays.InvalidOffsetAndLength(offset, it, bufferByteLength, ctorKind).throwRangeError()
            }
        }

        obj.setSlot(SlotName.ViewedArrayBuffer, buffer)
        obj.setSlot(SlotName.ByteLength, newByteLength)
        obj.setSlot(SlotName.ByteOffset, offset)
        obj.setSlot(SlotName.ArrayLength, newByteLength / elementSize)
    }

    @ECMAImpl("22.2.5.1.4")
    private fun initializeTypedArrayFromList(obj: JSValue, values: List<JSValue>) {
        ecmaAssert(obj is JSObject && obj.hasSlot(SlotName.TypedArrayName))
        allocateTypedArrayBuffer(obj, values.size)
        values.forEachIndexed { index, value ->
            Operations.set(obj, index.key(), value, true)
        }
    }

    @ECMAImpl("22.2.5.1.5")
    private fun initializeTypedArrayFromArrayLike(obj: JSValue, arrayLike: JSObject) {
        ecmaAssert(obj is JSObject && obj.hasSlot(SlotName.TypedArrayName))
        val length = Operations.lengthOfArrayLike(arrayLike)
        for (i in 0 until length)
            Operations.set(obj, i.key(), arrayLike.get(i), true)
    }

    @ECMAImpl("22.2.5.1.6")
    private fun allocateTypedArrayBuffer(obj: JSObject, length: Int) {
        val byteLength = kind.size * length
        val data = Operations.allocateArrayBuffer(realm, realm.arrayBufferCtor, byteLength)
        obj.setSlot(SlotName.ViewedArrayBuffer, data)
        obj.setSlot(SlotName.ByteLength, byteLength)
        obj.setSlot(SlotName.ByteOffset, 0)
        obj.setSlot(SlotName.ArrayLength, length)
    }
}

class JSInt8ArrayCtor(realm: Realm) : JSGenericTypedArrayCtor(realm, Operations.TypedArrayKind.Int8) {
    companion object {
        fun create(realm: Realm) = JSInt8ArrayCtor(realm).initialize()
    }
}

class JSUint8ArrayCtor(realm: Realm) : JSGenericTypedArrayCtor(realm, Operations.TypedArrayKind.Uint8) {
    companion object {
        fun create(realm: Realm) = JSUint8ArrayCtor(realm).initialize()
    }
}

class JSUint8CArrayCtor(realm: Realm) : JSGenericTypedArrayCtor(realm, Operations.TypedArrayKind.Uint8C) {
    companion object {
        fun create(realm: Realm) = JSUint8CArrayCtor(realm).initialize()
    }
}

class JSInt16ArrayCtor(realm: Realm) : JSGenericTypedArrayCtor(realm, Operations.TypedArrayKind.Int16) {
    companion object {
        fun create(realm: Realm) = JSInt16ArrayCtor(realm).initialize()
    }
}

class JSUint16ArrayCtor(realm: Realm) : JSGenericTypedArrayCtor(realm, Operations.TypedArrayKind.Uint16) {
    companion object {
        fun create(realm: Realm) = JSUint16ArrayCtor(realm).initialize()
    }
}

class JSInt32ArrayCtor(realm: Realm) : JSGenericTypedArrayCtor(realm, Operations.TypedArrayKind.Int32) {
    companion object {
        fun create(realm: Realm) = JSInt32ArrayCtor(realm).initialize()
    }
}

class JSUint32ArrayCtor(realm: Realm) : JSGenericTypedArrayCtor(realm, Operations.TypedArrayKind.Uint32) {
    companion object {
        fun create(realm: Realm) = JSUint32ArrayCtor(realm).initialize()
    }
}

class JSFloat32ArrayCtor(realm: Realm) : JSGenericTypedArrayCtor(realm, Operations.TypedArrayKind.Float32) {
    companion object {
        fun create(realm: Realm) = JSFloat32ArrayCtor(realm).initialize()
    }
}

class JSFloat64ArrayCtor(realm: Realm) : JSGenericTypedArrayCtor(realm, Operations.TypedArrayKind.Float64) {
    companion object {
        fun create(realm: Realm) = JSFloat64ArrayCtor(realm).initialize()
    }
}

class JSBigInt64ArrayCtor(realm: Realm) : JSGenericTypedArrayCtor(realm, Operations.TypedArrayKind.BigInt64) {
    companion object {
        fun create(realm: Realm) = JSBigInt64ArrayCtor(realm).initialize()
    }
}

class JSBigUint64ArrayCtor(realm: Realm) : JSGenericTypedArrayCtor(realm, Operations.TypedArrayKind.BigUint64) {
    companion object {
        fun create(realm: Realm) = JSBigUint64ArrayCtor(realm).initialize()
    }
}
