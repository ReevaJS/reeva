package com.reevajs.reeva.runtime.objects

import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.collections.JSSetObject
import com.reevajs.reeva.runtime.collections.MapData
import com.reevajs.reeva.runtime.memory.DataBlock
import com.reevajs.reeva.runtime.primitives.*
import com.reevajs.regexp.RegExp
import java.time.ZonedDateTime

@JvmInline
value class Slot<T> private constructor(val index: Int) {
    companion object {
        val ArrayBufferByteLength = nextSlot<Int>()
        val ArrayBufferData = nextSlot<DataBlock>()
        val ArrayBufferDetachKey = nextSlot<JSValue>()
        val ArrayLength = nextSlot<Int>()
        val BigIntData = nextSlot<JSBigInt>()
        val BooleanData = nextSlot<JSBoolean>()
        val ByteLength = nextSlot<Int>()
        val ByteOffset = nextSlot<Int>()
        val ContentType = nextSlot<JSValue>()
        val DataView = nextSlot<Unit>()
        val DateValue = nextSlot<ZonedDateTime?>()
        val Description = nextSlot<String>()
        val ErrorData = nextSlot<Unit>()
        val MapData = nextSlot<MapData>()
        val MappedParameterMap = nextSlot<JSObject>() // [[ParameterMap]], non-standard
        val NumberData = nextSlot<JSNumber>()
        val OriginalFlags = nextSlot<String>()
        val OriginalSource = nextSlot<String>()
        val PromiseFulfillReactions = nextSlot<MutableList<AOs.PromiseReaction>>()
        val PromiseIsHandled = nextSlot<Boolean>()
        val PromiseRejectReactions = nextSlot<MutableList<AOs.PromiseReaction>>()
        val PromiseResult = nextSlot<JSValue>()
        val PromiseState = nextSlot<AOs.PromiseState>()
        val ProxyHandler = nextSlot<JSObject?>()
        val ProxyTarget = nextSlot<JSObject>()
        val RegExpMatcher = nextSlot<RegExp>()
        val SetData = nextSlot<JSSetObject.SetData>()
        val StringData = nextSlot<JSString>()
        val SymbolData = nextSlot<JSSymbol>()
        val TypedArrayKind = nextSlot<AOs.TypedArrayKind>() // non-standard
        val TypedArrayName = nextSlot<String>()
        val UnmappedParameterMap = nextSlot<JSValue>() // [[ParameterMap]]
        val ViewedArrayBuffer = nextSlot<JSObject>()

        private var nextSlotIndex = 0

        fun <T> nextSlot() = Slot<T>(nextSlotIndex++)
    }
}
