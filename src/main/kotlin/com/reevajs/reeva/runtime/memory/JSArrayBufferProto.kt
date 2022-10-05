package com.reevajs.reeva.runtime.memory

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.JSNumber
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.toIntegerOrInfinity
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.attrs
import com.reevajs.reeva.utils.toValue
import kotlin.math.max
import kotlin.math.min

class JSArrayBufferProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "ArrayBuffer".toValue(), attrs { +conf })
        defineOwnProperty("constructor", realm.arrayBufferCtor, attrs { +conf; -enum; +writ })
        defineBuiltinGetter("byteLength", ::getByteLength, attrs { +conf; -enum })
        defineBuiltin("slice", 2, ::slice)
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSArrayBufferProto(realm).initialize()

        @ECMAImpl("25.1.5.1")
        @JvmStatic
        fun getByteLength(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (!AOs.requireInternalSlot(thisValue, Slot.ArrayBufferData))
                Errors.IncompatibleMethodCall("ArrayBuffer.prototype.byteLength").throwTypeError()
            if (AOs.isSharedArrayBuffer(thisValue))
                Errors.TODO("ArrayBuffer.prototype.getByteLength isSharedArrayBuffer").throwTypeError()

            if (AOs.isDetachedBuffer(thisValue))
                return JSNumber.ZERO
            return thisValue[Slot.ArrayBufferByteLength].toValue()
        }

        @ECMAImpl("25.1.5.3")
        @JvmStatic
        fun slice(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (!AOs.requireInternalSlot(thisValue, Slot.ArrayBufferData))
                Errors.IncompatibleMethodCall("ArrayBuffer.prototype.slice").throwTypeError()
            if (AOs.isSharedArrayBuffer(thisValue))
                Errors.TODO("ArrayBuffer.prototype.slice isSharedArrayBuffer 1").throwTypeError()
            if (AOs.isDetachedBuffer(thisValue))
                Errors.TODO("ArrayBuffer.prototype.slice isDetachedBuffer").throwTypeError()

            val length = thisValue[Slot.ArrayBufferByteLength]
            val relativeStart = arguments.argument(0).toIntegerOrInfinity()
            val first = when {
                relativeStart.number < 0 -> max(length + relativeStart.asInt, 0)
                relativeStart.isNegativeInfinity -> 0
                else -> min(relativeStart.asInt, length)
            }

            val relativeEnd = arguments.argument(1).let {
                if (it == JSUndefined) {
                    length.toValue()
                } else it.toIntegerOrInfinity()
            }

            val final = when {
                relativeEnd.isNegativeInfinity -> 0
                relativeEnd.number < 0 -> max(length + relativeEnd.asInt, 0)
                else -> min(relativeEnd.asInt, length)
            }

            val newLength = max(final - first, 0)

            val ctor = AOs.speciesConstructor(thisValue, Agent.activeAgent.getActiveRealm().arrayBufferCtor)
            val new = AOs.construct(ctor, listOf(newLength.toValue()))
            if (!AOs.requireInternalSlot(new, Slot.ArrayBufferData))
                Errors.ArrayBuffer.BadSpecies(new.toString()).throwTypeError()

            if (AOs.isSharedArrayBuffer(new))
                Errors.TODO("ArrayBuffer.prototype.slice isSharedArrayBuffer 2").throwTypeError()
            if (AOs.isDetachedBuffer(new))
                Errors.TODO("ArrayBuffer.prototype.slice isDetachedBuffer 1").throwTypeError()

            if (new.sameValue(thisValue))
                Errors.TODO("ArrayBuffer.prototype.slice SameValue").throwTypeError()

            if (new[Slot.ArrayBufferByteLength] < newLength)
                Errors.TODO("ArrayBuffer.prototype.slice newLength").throwTypeError()
            if (AOs.isDetachedBuffer(new))
                Errors.TODO("ArrayBuffer.prototype.slice isDetachedBuffer 2").throwTypeError()

            val fromBuf = thisValue[Slot.ArrayBufferData]
            val toBuf = new[Slot.ArrayBufferData]
            AOs.copyDataBlockBytes(toBuf, 0, fromBuf, first, newLength)
            return new
        }
    }
}
