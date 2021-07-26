package com.reevajs.reeva.runtime.memory

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors

class JSDataViewCtor private constructor(realm: Realm) : JSNativeFunction(realm, "DataView", 1) {
    override fun evaluate(arguments: JSArguments): JSValue {
        val newTarget = arguments.newTarget
        if (newTarget == JSUndefined)
            Errors.CtorCallWithoutNew("DataView").throwTypeError(realm)

        val (buffer, byteOffset, byteLength) = arguments.takeArgs(0..2)

        if (!Operations.requireInternalSlot(buffer, SlotName.ArrayBufferData))
            Errors.DataView.CtorBadBufferArg.throwTypeError(realm)

        val offset = byteOffset.toIndex(realm)

        if (Operations.isDetachedBuffer(buffer))
            Errors.TODO("DataViewCtor isDetachedBuffer 1").throwTypeError(realm)

        val bufferByteLength = buffer.getSlotAs<Int>(SlotName.ArrayBufferByteLength)
        if (offset > bufferByteLength)
            Errors.DataView.OutOfRangeOffset(offset, bufferByteLength).throwRangeError(realm)

        val viewByteLength = if (byteLength != JSUndefined) {
            byteLength.toIndex(realm).also {
                if (offset + it > bufferByteLength)
                    Errors.DataView.OutOfRangeOffset(offset + it, bufferByteLength).throwRangeError(realm)
            }
        } else bufferByteLength - offset

        val theRealm = (newTarget as? JSObject)?.realm ?: realm
        val obj = Operations.ordinaryCreateFromConstructor(realm, newTarget, theRealm.dataViewProto, listOf(SlotName.DataView))
        if (Operations.isDetachedBuffer(buffer))
            Errors.TODO("DataViewCtor isDetachedBuffer 2").throwTypeError(realm)

        obj.setSlot(SlotName.ViewedArrayBuffer, buffer)
        obj.setSlot(SlotName.ByteLength, viewByteLength)
        obj.setSlot(SlotName.ByteOffset, offset)
        return obj
    }

    companion object {
        fun create(realm: Realm) = JSDataViewCtor(realm).initialize()
    }
}
