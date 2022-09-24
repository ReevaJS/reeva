package com.reevajs.reeva.runtime.objects

import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.collections.JSMapObject
import com.reevajs.reeva.runtime.collections.JSSetObject
import com.reevajs.reeva.runtime.memory.DataBlock
import com.reevajs.reeva.runtime.primitives.*
import com.reevajs.regexp.RegExp
import java.time.ZonedDateTime

@JvmInline
value class SlotName<T>(val index: Int) {
    companion object {
        val ArrayBufferData = nextSlot<DataBlock>()
        val ArrayBufferByteLength = nextSlot<Int>()
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
        val MapData = nextSlot<JSMapObject.MapData>()
        val NumberData = nextSlot<JSNumber>()
        val OriginalSource = nextSlot<String>()
        val OriginalFlags = nextSlot<String>()
        val UnmappedParameterMap = nextSlot<JSValue>() // [[ParameterMap]]
        val MappedParameterMap = nextSlot<JSObject>() // [[ParameterMap]], non-standard
        val PromiseFulfillReactions = nextSlot<MutableList<Operations.PromiseReaction>>()
        val PromiseIsHandled = nextSlot<Boolean>()
        val PromiseState = nextSlot<Operations.PromiseState>()
        val PromiseRejectReactions = nextSlot<MutableList<Operations.PromiseReaction>>()
        val PromiseResult = nextSlot<JSValue>()
        val ProxyHandler = nextSlot<JSObject?>()
        val ProxyTarget = nextSlot<JSObject>()
        val RegExpMatcher = nextSlot<RegExp>()
        val SetData = nextSlot<JSSetObject.SetData>()
        val StringData = nextSlot<JSString>()
        val SymbolData = nextSlot<JSSymbol>()
        val TypedArrayKind = nextSlot<Operations.TypedArrayKind>() // non-standard
        val TypedArrayName = nextSlot<String>()
        val ViewedArrayBuffer = nextSlot<JSObject>()

        private var nextSlotIndex = 0

        private fun <T> nextSlot() = SlotName<T>(nextSlotIndex++)
    }
}
