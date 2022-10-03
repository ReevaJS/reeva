package com.reevajs.reeva.runtime.objects

import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.collections.MapData
import com.reevajs.reeva.runtime.collections.SetData
import com.reevajs.reeva.runtime.memory.DataBlock
import com.reevajs.reeva.runtime.primitives.*
import com.reevajs.regexp.RegExp
import java.math.BigInteger
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
        val Calendar = nextSlot<JSObject>()
        val ContentType = nextSlot<JSValue>()
        val DataView = nextSlot<Unit>()
        val DateValue = nextSlot<ZonedDateTime?>()
        val Day = nextSlot<Int>()
        val Days = nextSlot<Int>()
        val Description = nextSlot<String>()
        val ErrorData = nextSlot<Unit>()
        val Hour = nextSlot<Int>()
        val Hours = nextSlot<Int>()
        val Identifier = nextSlot<String>()
        val InitializedTemporalCalendar = nextSlot<Unit>()
        val InitializedTemporalDate = nextSlot<Unit>()
        val InitializedTemporalDateTime = nextSlot<Unit>()
        val InitializedTemporalDuration = nextSlot<Unit>()
        val InitializedTemporalInstant = nextSlot<Unit>()
        val InitializedTemporalTime = nextSlot<Unit>()
        val InitializedTemporalYearMonth = nextSlot<Unit>()
        val InitializedTemporalMonthDay = nextSlot<Unit>()
        val InitializedTemporalZonedDateTime = nextSlot<Unit>()
        val ISODay = nextSlot<Int>()
        val ISOHour = nextSlot<Int>()
        val ISOMicrosecond = nextSlot<Int>()
        val ISOMillisecond = nextSlot<Int>()
        val ISOMinute = nextSlot<Int>()
        val ISOMonth = nextSlot<Int>()
        val ISONanosecond = nextSlot<BigInteger>()
        val ISOSecond = nextSlot<Int>()
        val ISOYear = nextSlot<Int>()
        val LargestUnit = nextSlot<String>()
        val MapData = nextSlot<MapData>()
        val MappedParameterMap = nextSlot<JSObject>() // [[ParameterMap]], non-standard
        val Microseconds = nextSlot<Int>()
        val Microsecond = nextSlot<Int>()
        val Millisecond = nextSlot<Int>()
        val Milliseconds = nextSlot<Int>()
        val Minutes = nextSlot<Int>()
        val Month = nextSlot<Int>()
        val Months = nextSlot<Int>()
        val Nanosecond = nextSlot<BigInteger>()
        val Nanoseconds = nextSlot<BigInteger>()
        val NumberData = nextSlot<JSNumber>()
        val Options = nextSlot<JSObject>()
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
        val RoundingIncrement = nextSlot<Int>()
        val RoundingMode = nextSlot<String>()
        val Second = nextSlot<Int>()
        val Seconds = nextSlot<Int>()
        val SetData = nextSlot<SetData>()
        val SmallestUnit = nextSlot<String>()
        val StringData = nextSlot<JSString>()
        val SymbolData = nextSlot<JSSymbol>()
        val TimeZone = nextSlot<JSObject>()
        val TypedArrayKind = nextSlot<AOs.TypedArrayKind>() // non-standard
        val TypedArrayName = nextSlot<String>()
        val UnmappedParameterMap = nextSlot<JSValue>() // [[ParameterMap]]
        val ViewedArrayBuffer = nextSlot<JSObject>()
        val Week = nextSlot<Int>()
        val Weeks = nextSlot<Int>()
        val Year = nextSlot<Int>()
        val Years = nextSlot<Int>()

        private var nextSlotIndex = 0

        fun <T> nextSlot() = Slot<T>(nextSlotIndex++)
    }
}
