package com.reevajs.reeva.runtime.memory

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors

class JSDataViewCtor private constructor(realm: Realm) : JSNativeFunction(realm, "DataView", 1) {
    override fun evaluate(arguments: JSArguments): JSValue {
        val newTarget = arguments.newTarget
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

        val obj = Operations.ordinaryCreateFromConstructor(
            newTarget,
            listOf(SlotName.DataView),
            defaultProto = Realm::dataViewProto,
        )
        if (Operations.isDetachedBuffer(buffer))
            Errors.TODO("DataViewCtor isDetachedBuffer 2").throwTypeError()

        obj.setSlot(SlotName.ViewedArrayBuffer, buffer)
        obj.setSlot(SlotName.ByteLength, viewByteLength)
        obj.setSlot(SlotName.ByteOffset, offset)
        return obj
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSDataViewCtor(realm).initialize()
    }
}
