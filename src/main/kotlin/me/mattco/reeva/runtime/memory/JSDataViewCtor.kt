package me.mattco.reeva.runtime.memory

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.SlotName
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.runtime.toIndex
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.takeArgs

class JSDataViewCtor private constructor(realm: Realm) : JSNativeFunction(realm, "DataView", 1) {
    init {
        isConstructable = true
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        val newTarget = super.newTarget
        if (newTarget == JSUndefined)
            Errors.CtorCallWithoutNew("DataView").throwTypeError()

        val (buffer, byteOffset, byteLength) = arguments.takeArgs(0..2)

        if (!Operations.requireInternalSlot(buffer, SlotName.ArrayBufferData))
            Errors.DataView.CtorBadBufferArg.throwTypeError()

        val offset = byteOffset.toIndex()

        if (Operations.isDetachedBuffer(buffer))
            Errors.TODO("DataViewCtor isDetachedBuffer 1").throwTypeError()

        val bufferByteLength = buffer.getSlotAs<Int>(SlotName.ArrayBufferByteLength)
        if (offset > bufferByteLength)
            Errors.DataView.OutOfRangeOffset(offset, bufferByteLength).throwRangeError()

        val viewByteLength = if (byteLength != JSUndefined) {
            byteLength.toIndex().also {
                if (offset + it > bufferByteLength)
                    Errors.DataView.OutOfRangeOffset(offset + it, bufferByteLength).throwRangeError()
            }
        } else bufferByteLength - offset

        val theRealm = (newTarget as? JSObject)?.realm ?: realm
        val obj = Operations.ordinaryCreateFromConstructor(newTarget, theRealm.dataViewProto, listOf(SlotName.DataView))
        if (Operations.isDetachedBuffer(buffer))
            Errors.TODO("DataViewCtor isDetachedBuffer 2").throwTypeError()

        obj.setSlot(SlotName.ViewedArrayBuffer, buffer)
        obj.setSlot(SlotName.ByteLength, viewByteLength)
        obj.setSlot(SlotName.ByteOffset, offset)
        return obj
    }

    companion object {
        fun create(realm: Realm) = JSDataViewCtor(realm).initialize()
    }
}
