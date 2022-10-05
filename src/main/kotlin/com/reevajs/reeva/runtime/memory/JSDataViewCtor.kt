package com.reevajs.reeva.runtime.memory

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.toIndex
import com.reevajs.reeva.utils.Errors

class JSDataViewCtor private constructor(realm: Realm) : JSNativeFunction(realm, "DataView", 1) {
    override fun evaluate(arguments: JSArguments): JSValue {
        val newTarget = arguments.newTarget
        if (newTarget == JSUndefined)
            Errors.CtorCallWithoutNew("DataView").throwTypeError()

        val (buffer, byteOffset, byteLength) = arguments.takeArgs(0..2)

        if (!AOs.requireInternalSlot(buffer, Slot.ArrayBufferData))
            Errors.DataView.CtorBadBufferArg.throwTypeError()

        val offset = byteOffset.toIndex()

        if (AOs.isDetachedBuffer(buffer))
            Errors.TODO("DataViewCtor isDetachedBuffer 1").throwTypeError()

        val bufferByteLength = buffer[Slot.ArrayBufferByteLength]
        if (offset > bufferByteLength)
            Errors.DataView.OutOfRangeOffset(offset, bufferByteLength).throwRangeError()

        val viewByteLength = if (byteLength != JSUndefined) {
            byteLength.toIndex().also {
                if (offset + it > bufferByteLength)
                    Errors.DataView.OutOfRangeOffset(offset + it, bufferByteLength).throwRangeError()
            }
        } else bufferByteLength - offset

        val obj = AOs.ordinaryCreateFromConstructor(
            newTarget,
            listOf(Slot.DataView),
            defaultProto = Realm::dataViewProto,
        )
        if (AOs.isDetachedBuffer(buffer))
            Errors.TODO("DataViewCtor isDetachedBuffer 2").throwTypeError()

        obj[Slot.ViewedArrayBuffer] = buffer
        obj[Slot.ByteLength] = viewByteLength
        obj[Slot.ByteOffset] = offset
        return obj
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSDataViewCtor(realm).initialize()
    }
}
