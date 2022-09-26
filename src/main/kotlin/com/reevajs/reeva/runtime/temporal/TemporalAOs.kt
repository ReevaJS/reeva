package com.reevajs.reeva.runtime.temporal

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.PropertyKey
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.JSNumber
import com.reevajs.reeva.runtime.primitives.JSString
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.toBoolean
import com.reevajs.reeva.runtime.toJSString
import com.reevajs.reeva.runtime.toNumber
import com.reevajs.reeva.utils.*
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.floor
import kotlin.math.pow

object TemporalAOs {
    val realm: Realm
        get() = Agent.activeAgent.getActiveRealm()

    private val NS_MAX_INSTANT = BigInteger.valueOf(864L).pow(19)
    private val NS_MIN_INSTANT = -NS_MAX_INSTANT
    private val NS_PER_DAY = BigInteger.valueOf(864L).pow(11)

    private val MIN_UTC_INSTANT: Long = (-8.64 * 10.0.pow(21)).toLong()
    private val MAX_UTC_INSTANT: Long = (-8.64 * 10.0.pow(21)).toLong()

    @JvmStatic
    @ECMAImpl("2.3.2")
    fun systemUTCEpochNanoseconds(): BigInteger {
        // 1. Let ns be the approximate current UTC date and time, in nanoseconds since the epoch.
        val now = Instant.now().let {
            it.toEpochMilli().toBigInteger() * 1_000_000L.toBigInteger() + it.nano.toBigInteger()
        }

        // 2. Set ns to the result of clamping ns between nsMinInstant and nsMaxInstant.
        // 3. Return ℤ(ns).
        return now.coerceIn(NS_MIN_INSTANT, NS_MAX_INSTANT)
    }

    @JvmStatic
    @ECMAImpl("2.3.3")
    fun systemInstant(): JSObject {
        // 1. Let ns be ! SystemUTCEpochNanoseconds().
        val ns = systemUTCEpochNanoseconds()

        // 2. Return ! CreateTemporalInstant(ns).
        return createTemporalInstant(ns)
    }

    @JvmStatic
    @ECMAImpl("3.5.7")
    fun isValidISODate(year: Int, month: Int, day: Int): Boolean {
        // 1. If month < 1 or month > 12, then
        if (month !in 1..12) {
            // a. Return false.
            return false
        }

        // 2. Let daysInMonth be ! ISODaysInMonth(year, month).
        val daysInMonth = isoDaysInMonth(year, month)

        // 3. If day < 1 or day > daysInMonth, then
        //    a. Return false.
        // 4. Return true.
        return day in 1..daysInMonth
    }

    @JvmStatic
    @ECMAImpl("4.5.4")
    fun isValidTime(
        hour: Int,
        minute: Int,
        second: Int,
        millisecond: Int,
        microsecond: Int,
        nanosecond: BigInteger
    ): Boolean {
        // 1. If hour < 0 or hour > 23, then
        //    a. Return false.
        // 2. If minute < 0 or minute > 59, then
        //    a. Return false.
        // 3. If second < 0 or second > 59, then
        //    a. Return false.
        // 4. If millisecond < 0 or millisecond > 999, then
        //    a. Return false.
        // 5. If microsecond < 0 or microsecond > 999, then
        //    a. Return false.
        // 6. If nanosecond < 0 or nanosecond > 999, then
        //    a. Return false.
        // 7. Return true.
        return hour in 0..23 &&
            minute in 0..59 &&
            second in 0..59 &&
            millisecond in 0..999 &&
            microsecond in 0..999 &&
            nanosecond in BigInteger.ZERO..BigInteger.valueOf(999L)
    }

    @JvmStatic
    @ECMAImpl("5.5.1")
    fun getEpochFromISOParts(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
        millisecond: Int,
        microsecond: Int,
        nanosecond: BigInteger,
    ): BigInteger {
        // 1. Assert: IsValidISODate(year, month, day) is true.
        ecmaAssert(isValidISODate(year, month, day))

        // 2. Let date be MakeDay(𝔽(year), 𝔽(month - 1), 𝔽(day)).
        val date = AOs.makeDay(year, month - 1, day)

        // 3. Let time be MakeTime(𝔽(hour), 𝔽(minute), 𝔽(second), 𝔽(millisecond)).
        val time = AOs.makeTime(hour, minute, second, millisecond)

        // 4. Let ms be MakeDate(date, time).
        val ms = AOs.makeDate(date, time)

        // 5. Assert: ms is finite.
        // 6. Return ℤ(ℝ(ms) × 10^6 + microsecond × 10^3 + nanosecond).
        return ms.toBigInteger() * BigInteger.valueOf(1_000_000L) +
            microsecond.toBigInteger() * BigInteger.valueOf(1000L) + nanosecond
    }

    @JvmStatic
    @ECMAImpl("7.5.5")
    fun createDurationRecord(
        years: Int,
        months: Int,
        weeks: Int,
        days: Int,
        hours: Int,
        minutes: Int,
        seconds: Int,
        milliseconds: Int,
        microseconds: Int,
        nanoseconds: BigInteger,
    ): DurationRecord {
        val duration = DurationRecord(
            years, months, weeks, days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds,
        )

        // 1. If ! IsValidDuration(years, months, weeks, days, hours, minutes, seconds, milliseconds, microseconds,
        //    nanoseconds) is false, throw a RangeError exception.
        if (!isValidDuration(duration))
            Errors.TODO("createDurationRecord").throwRangeError()

        // 2. Return the Record { [[Years]]: ℝ(𝔽(years)), [[Months]]: ℝ(𝔽(months)), [[Weeks]]: ℝ(𝔽(weeks)),
        //    [[Days]]: ℝ(𝔽(days)), [[Hours]]: ℝ(𝔽(hours)), [[Minutes]]: ℝ(𝔽(minutes)), [[Seconds]]: ℝ(𝔽(seconds)),
        //    [[Milliseconds]]: ℝ(𝔽(milliseconds)), [[Microseconds]]: ℝ(𝔽(microseconds)),
        //    [[Nanoseconds]]: ℝ(𝔽(nanoseconds)) }.
        return duration
    }

    @JvmStatic
    @ECMAImpl("7.5.9")
    fun toTemporalDurationRecord(temporalDurationLike: JSValue): DurationRecord {
        // 1. If Type(temporalDurationLike) is not Object, then
        if (temporalDurationLike !is JSObject) {
            // a. Let string be ? ToString(temporalDurationLike).
            val string = temporalDurationLike.toJSString()

            // b. Return ? ParseTemporalDurationString(string).
            return parseTemporalDurationString(string.string)
        }

        // 2. If temporalDurationLike has an [[InitializedTemporalDuration]] internal slot, then
        if (Slot.InitializedTemporalDuration in temporalDurationLike) {
            // a. Return ! CreateDurationRecord(temporalDurationLike.[[Years]], temporalDurationLike.[[Months]],
            //    temporalDurationLike.[[Weeks]], temporalDurationLike.[[Days]], temporalDurationLike.[[Hours]],
            //    temporalDurationLike.[[Minutes]], temporalDurationLike.[[Seconds]],
            //    temporalDurationLike.[[Milliseconds]], temporalDurationLike.[[Microseconds]],
            //    temporalDurationLike.[[Nanoseconds]]).
            return createDurationRecord(
                temporalDurationLike[Slot.Years],
                temporalDurationLike[Slot.Months],
                temporalDurationLike[Slot.Weeks],
                temporalDurationLike[Slot.Days],
                temporalDurationLike[Slot.Hours],
                temporalDurationLike[Slot.Minutes],
                temporalDurationLike[Slot.Seconds],
                temporalDurationLike[Slot.Milliseconds],
                temporalDurationLike[Slot.Microseconds],
                temporalDurationLike[Slot.Nanoseconds],
            )
        }

        // 3. Let result be a new Duration Record with each field set to 0.
        // 4. Let partial be ? ToTemporalPartialDurationRecord(temporalDurationLike).
        val partial = toTemporalPartialDurationRecord(temporalDurationLike)

        // 5. For each row of Table 8, except the header row, in table order, do
        //    a. Let fieldName be the Field Name value of the current row.
        //    b. Let value be the value of the field of partial whose name is fieldName.
        //    c. If value is not undefined, then
        //       i. Set the field of result whose name is fieldName to value.
        val result = partial.toDurationRecord()

        // 6. If ! IsValidDuration(result.[[Years]], result.[[Months]], result.[[Weeks]], result.[[Days]],
        //    result.[[Hours]], result.[[Minutes]], result.[[Seconds]], result.[[Milliseconds]],
        //    result.[[Microseconds]], result.[[Nanoseconds]]) is false, then
        if (!isValidDuration(result)) {
            // a. Throw a RangeError exception.
            Errors.TODO("toTemporalDurationRecord").throwRangeError()
        }

        // 7. Return result.
        return result
    }

    @JvmStatic
    @ECMAImpl("7.5.10")
    fun durationSign(duration: DurationRecord): Int {
        // For each value v of « years, months, weeks, days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds », do
        //    a. If v < 0, return -1.
        //    b. If v > 0, return 1.
        val comparisons = listOf(
            duration.years.compareTo(0),
            duration.months.compareTo(0),
            duration.weeks.compareTo(0),
            duration.days.compareTo(0),
            duration.hours.compareTo(0),
            duration.minutes.compareTo(0),
            duration.seconds.compareTo(0),
            duration.milliseconds.compareTo(0),
            duration.microseconds.compareTo(0),
            duration.nanoseconds.compareTo(BigInteger.ZERO),
        )

        for (value in comparisons) {
            when {
                value < 0 -> return -1
                value > 0 -> return 1
                else -> {}
            }
        }

        // 2. Return 0.
        return 0
    }

    @JvmStatic
    @ECMAImpl("7.5.11")
    fun isValidDuration(duration: DurationRecord): Boolean {
        // 1. Let sign be ! DurationSign(years, months, weeks, days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds).
        val sign = durationSign(duration)

        // 2. For each value v of « years, months, weeks, days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds », do
        val comparisons = listOf(
            duration.years.compareTo(0),
            duration.months.compareTo(0),
            duration.weeks.compareTo(0),
            duration.days.compareTo(0),
            duration.hours.compareTo(0),
            duration.minutes.compareTo(0),
            duration.seconds.compareTo(0),
            duration.milliseconds.compareTo(0),
            duration.microseconds.compareTo(0),
            duration.nanoseconds.compareTo(BigInteger.ZERO),
        )

        for (value in comparisons) {
            // a. If 𝔽(v) is not finite, return false.

            when {
                // b. If v < 0 and sign > 0, return false.
                value < 0 && sign > 0 -> return false

                // c. If v > 0 and sign < 0, return false.
                value > 0 && sign < 0 -> return false
                else -> {}
            }
        }

        // 3. Return true.
        return true
    }

    @JvmStatic
    @ECMAImpl("7.5.13")
    fun toTemporalPartialDurationRecord(temporalDurationLike: JSValue): PartialDurationRecord {
        // 1. If Type(temporalDurationLike) is not Object, then
        if (temporalDurationLike !is JSObject) {
            // a. Throw a TypeError exception.
            Errors.TODO("toTemporalPartialDurationRecord 1").throwTypeError()
        }

        // 2. Let result be a new partial Duration Record with each field set to undefined.
        val result = PartialDurationRecord()

        // 3. Let any be false.
        var any = false

        // 4. For each row of Table 8, except the header row, in table order, do
        fun processRow(property: String, setter: (value: Int) -> Unit) {
            // a. Let property be the Property Name value of the current row.
            // b. Let value be ? Get(temporalDurationLike, property).
            val value = temporalDurationLike.get(property)

            // c. If value is not undefined, then
            if (value != JSUndefined) {
                // i. Set any to true.
                any = true

                // ii. Set value to ? ToIntegerWithoutRounding(value).
                val intValue = toIntegerWithoutRounding(value)

                // iii. Let fieldName be the Field Name value of the current row.
                // iv. Set the field of result whose name is fieldName to value.
                setter(intValue)
            }
        }

        processRow("days") { result.days = it }
        processRow("hours") { result.hours = it }
        processRow("microseconds") { result.microseconds = it }
        processRow("milliseconds") { result.milliseconds = it }
        processRow("minutes") { result.minutes = it }
        processRow("months") { result.months = it }
        processRow("nanoseconds") { result.nanoseconds = it.toBigInteger() }
        processRow("seconds") { result.seconds = it }
        processRow("weeks") { result.weeks = it }
        processRow("years") { result.years = it }

        // 5. If any is false, then
        if (!any) {
            // a. Throw a TypeError exception.
            Errors.TODO("toTemporalPartialDurationRecord 2").throwTypeError()
        }

        // 6. Return result.
        return result
    }

    @JvmStatic
    @ECMAImpl("7.5.14")
    fun createTemporalDuration(years: Int, months: Int, weeks: Int, days: Int, hours: Int, minutes: Int, seconds: Int, milliseconds: Int, microseconds: Int, nanoseconds: BigInteger, newTarget: JSObject? = null): JSObject {
        TODO()
    }

    @JvmStatic
    @ECMAImpl("7.5.18")
    fun balanceDuration(days: Int, hours: Int, minutes: Int, seconds: Int, milliseconds: Int, microseconds: Int, nanoseconds: BigInteger, largestUnit: String, relativeTo: JSObject? = null): DurationRecord {
        // 1. Let balanceResult be ? BalancePossiblyInfiniteDuration(days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds, largestUnit, relativeTo).
        val balanceResult = balancePossiblyInfiniteDuration(days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds, largestUnit, relativeTo)

        // 2. If balanceResult is positive overflow or negative overflow, then
        if (balanceResult !is InfiniteDurationResult.Balanced) {
            // a. Throw a RangeError exception.
            Errors.TODO("balanceDuration").throwRangeError()
        }
        // 3. Else,
        else {
            // a. Return balanceResult.
            return balanceResult.result
        }
    }

    sealed class InfiniteDurationResult {
        object PositiveOverflow : InfiniteDurationResult()

        object NegativeOverflow : InfiniteDurationResult()

        class Balanced(val result: DurationRecord) : InfiniteDurationResult()
    }

    @JvmStatic
    @ECMAImpl("7.5.19")
    fun balancePossiblyInfiniteDuration(days: Int, hours: Int, minutes: Int, seconds: Int, milliseconds: Int, microseconds: Int, nanoseconds: BigInteger, largestUnit: String, relativeTo: JSObject? = null): InfiniteDurationResult {
        TODO()
    }

    @JvmStatic
    @ECMAImpl("7.5.26")
    fun roundDuration(
        duration: DurationRecord,
        increment: Int,
        unit: String,
        roundingMode: String,
        relativeTo_: JSObject? = null,
    ): RoundDurationRecord {
        TODO()
    }

    data class RoundDurationRecord(val duration: DurationRecord, val remainder: BigInteger)

    @JvmStatic
    @ECMAImpl("8.5.1")
    fun isValidEpochNanoseconds(epochNanoseconds: BigInteger): Boolean {
        // 1. Assert: Type(epochNanoseconds) is BigInt.
        // 2. If ℝ(epochNanoseconds) < nsMinInstant or ℝ(epochNanoseconds) > nsMaxInstant, then
        //     a. Return false.
        // 3. Return true.
        return epochNanoseconds in NS_MIN_INSTANT..NS_MAX_INSTANT
    }

    @JvmStatic
    @ECMAImpl("8.5.2")
    fun createTemporalInstant(epochNanoseconds: BigInteger, newTarget: JSValue? = null): JSObject {
        // 1. Assert: Type(epochNanoseconds) is BigInt.
        // 2. Assert: ! IsValidEpochNanoseconds(epochNanoseconds) is true.
        ecmaAssert(isValidEpochNanoseconds(epochNanoseconds))

        // 3. If newTarget is not present, set newTarget to %Temporal.Instant%.
        // 4. Let object be ? OrdinaryCreateFromConstructor(newTarget, "%Temporal.Instant.prototype%", « [[InitializedTemporalInstant]], [[Nanoseconds]] »).
        val obj = AOs.ordinaryCreateFromConstructor(
            newTarget ?: realm.instantCtor,
            listOf(Slot.InitializedTemporalInstant, Slot.Nanoseconds),
            defaultProto = Realm::instantProto,
        )

        // 5. Set object.[[Nanoseconds]] to epochNanoseconds.
        obj[Slot.Nanoseconds] = epochNanoseconds

        // 6. Return object.
        return obj
    }

    @JvmStatic
    @ECMAImpl("8.5.3")
    fun toTemporalInstant(item: JSValue): JSObject {
        // 1. If Type(item) is Object, then
        if (item is JSObject) {
            // a. If item has an [[InitializedTemporalInstant]] internal slot, then
            if (Slot.InitializedTemporalInstant in item) {
                // i. Return item.
                return item
            }

            // b. If item has an [[InitializedTemporalZonedDateTime]] internal slot, then
            if (Slot.InitializedTemporalZonedDateTime in item) {
                // i. Return ! CreateTemporalInstant(item.[[Nanoseconds]]).
                return createTemporalInstant(item[Slot.Nanoseconds])
            }
        }

        // 2. Let string be ? ToString(item).
        val string = item.toJSString()

        // 3. Let epochNanoseconds be ? ParseTemporalInstant(string).
        val epochNanoseconds = parseTemporalInstant(string.string)

        // 4. Return ! CreateTemporalInstant(ℤ(epochNanoseconds)).
        return createTemporalInstant(epochNanoseconds)
    }

    @JvmStatic
    @ECMAImpl("8.5.4")
    fun parseTemporalInstant(isoString: String): BigInteger {
        // 1. Assert: Type(isoString) is String.
        // 2. Let result be ? ParseTemporalInstantString(isoString).
        val result = parseTemporalInstantString(isoString)

        // 3. Let offsetString be result.[[TimeZoneOffsetString]].
        val offsetString = result.offset

        // 4. Assert: offsetString is not undefined.
        ecmaAssert(offsetString != null)

        // 5. Let utc be GetEpochFromISOParts(result.[[Year]], result.[[Month]], result.[[Day]], result.[[Hour]], result.[[Minute]], result.[[Second]], result.[[Millisecond]], result.[[Microsecond]], result.[[Nanosecond]]).
        val utc = getEpochFromISOParts(
            result.year,
            result.month,
            result.day,
            result.hour,
            result.minute,
            result.second,
            result.millisecond,
            result.microsecond,
            result.nanosecond,
        )

        // 6. Let offsetNanoseconds be ? ParseTimeZoneOffsetString(offsetString).
        val offsetNanoseconds = parseTimeZoneOffsetString(offsetString)

        // 7. Let result be utc - ℤ(offsetNanoseconds).
        val result2 = utc - offsetNanoseconds.toBigInteger()

        // 8. If ! IsValidEpochNanoseconds(result) is false, then
        if (!isValidEpochNanoseconds(result2)) {
            // a. Throw a RangeError exception.
            Errors.TODO("parseTemporalInstant").throwRangeError()
        }

        // 9. Return result.
        return result2
    }

    @JvmStatic
    @ECMAImpl("8.5.5")
    fun compareEpochNanoseconds(one: BigInteger, two: BigInteger): Int {
        // 1. If epochNanosecondsOne > epochNanosecondsTwo, return 1.
        // 2. If epochNanosecondsOne < epochNanosecondsTwo, return -1.
        // 3. Return 0.
        return one.compareTo(two)
    }

    @JvmStatic
    @ECMAImpl("8.5.6")
    fun addInstant(
        epochNanoseconds: BigInteger,
        hours: Int,
        minutes: Int,
        seconds: Int,
        milliseconds: Int,
        microseconds: Int,
        nanoseconds: BigInteger,
    ): BigInteger {
        val ten9 = 1_000_000_000L.toBigInteger()

        // 1. Let result be epochNanoseconds + ℤ(nanoseconds) + ℤ(microseconds) × 1000ℤ + ℤ(milliseconds) × 10^6ℤ +
        //    ℤ(seconds) × 10^9ℤ + ℤ(minutes) × 60ℤ × 10^9ℤ + ℤ(hours) × 3600ℤ × 10^9ℤ.
        val result = epochNanoseconds + nanoseconds +
            microseconds.toBigInteger() * 1_000L.toBigInteger() +
            milliseconds.toBigInteger() * 1_000_000L.toBigInteger() +
            seconds.toBigInteger() * ten9 +
            minutes.toBigInteger() * 60L.toBigInteger() * ten9 +
            hours.toBigInteger() * 3_600L.toBigInteger() * ten9

        // 2. If ! IsValidEpochNanoseconds(result) is false, throw a RangeError exception.
        if (!isValidEpochNanoseconds(result))
            Errors.TODO("addInstant").throwRangeError(realm)

        // 3. Return result.
        return result
    }

    @JvmStatic
    @ECMAImpl("8.5.7")
    fun differenceInstant(nanoseconds1: BigInteger, nanoseconds2: BigInteger, roundingIncrement: Int, smallestUnit: String, largestUnit: String, roundingMode: String): DurationRecord {
        // 1. Assert: Type(ns1) is BigInt.
        // 2. Assert: Type(ns2) is BigInt.
        // 3. Assert: The following step cannot fail due to overflow in the Number domain because abs(ns2 - ns1) ≤ 2 × nsMaxInstant.
        // 4. Let roundResult be ! RoundDuration(0, 0, 0, 0, 0, 0, 0, 0, 0, ns2 - ns1, roundingIncrement, smallestUnit, roundingMode).[[DurationRecord]].
        val roundResult = roundDuration(DurationRecord(0, 0, 0, 0, 0, 0, 0, 0, 0, nanoseconds2 - nanoseconds1), roundingIncrement, smallestUnit, roundingMode).duration

        // 5. Assert: roundResult.[[Days]] is 0.
        ecmaAssert(roundResult.days == 0)

        // 6. Return ! BalanceDuration(0, roundResult.[[Hours]], roundResult.[[Minutes]], roundResult.[[Seconds]], roundResult.[[Milliseconds]], roundResult.[[Microseconds]], roundResult.[[Nanoseconds]], largestUnit).
        return balanceDuration(0, roundResult.hours, roundResult.minutes, roundResult.seconds, roundResult.milliseconds, roundResult.microseconds, roundResult.nanoseconds, largestUnit)
    }

    @JvmStatic
    @ECMAImpl("8.5.10")
    fun differenceTemporalInstant(isUntil: Boolean, instant: JSObject, other: JSValue, options: JSValue): JSObject {
        // 1. If operation is since, let sign be -1. Otherwise, let sign be 1.
        val sign = if (isUntil) 1 else -1

        // 2. Set other to ? ToTemporalInstant(other).
        val other_ = toTemporalInstant(other)

        // 3. Let settings be ? GetDifferenceSettings(operation, options, time, « », "nanosecond", "second").
        val settings = getDifferenceSettings(isUntil, options, "time", emptyList(), "nanosecond", "second")

        // 4. Let result be ! DifferenceInstant(instant.[[Nanoseconds]], other.[[Nanoseconds]],
        //    settings.[[RoundingIncrement]], settings.[[SmallestUnit]], settings.[[LargestUnit]],
        //    settings.[[RoundingMode]]).
        val result = differenceInstant(
            instant[Slot.Nanoseconds],
            other_[Slot.Nanoseconds],
            settings.roundingIncrement,
            settings.smallestUnit,
            settings.largestUnit,
            settings.roundingMode
        )

        // 5. Return ! CreateTemporalDuration(0, 0, 0, 0, sign × result.[[Hours]], sign × result.[[Minutes]],
        //    sign × result.[[Seconds]], sign × result.[[Milliseconds]], sign × result.[[Microseconds]],
        //    sign × result.[[Nanoseconds]]).
        return createTemporalDuration(
            0,
            0,
            0,
            0,
            sign * result.hours,
            sign * result.minutes,
            sign * result.seconds,
            sign * result.milliseconds,
            sign * result.microseconds,
            sign.toBigInteger() * result.nanoseconds,
        )
    }

    @JvmStatic
    @ECMAImpl("8.5.11")
    fun addDurationToOrSubtractDurationFromInstant(
        isAdd: Boolean,
        instant: JSObject,
        temporalDurationLike: JSValue,
    ): JSObject {
        // 1. If operation is subtract, let sign be -1. Otherwise, let sign be 1.
        val sign = if (isAdd) 1 else -1

        // 2. Let duration be ? ToTemporalDurationRecord(temporalDurationLike).
        val duration = toTemporalDurationRecord(temporalDurationLike)

        // 3. If duration.[[Days]] is not 0, throw a RangeError exception.
        if (duration.days != 0)
            Errors.TODO("addDurationToOrSubtractDurationFromInstant 1").throwRangeError()

        // 4. If duration.[[Months]] is not 0, throw a RangeError exception.
        if (duration.months != 0)
            Errors.TODO("addDurationToOrSubtractDurationFromInstant 2").throwRangeError()

        // 5. If duration.[[Weeks]] is not 0, throw a RangeError exception.
        if (duration.weeks != 0)
            Errors.TODO("addDurationToOrSubtractDurationFromInstant 3").throwRangeError()

        // 6. If duration.[[Years]] is not 0, throw a RangeError exception.
        if (duration.years != 0)
            Errors.TODO("addDurationToOrSubtractDurationFromInstant 4").throwRangeError()

        // 7. Let ns be ? AddInstant(instant.[[Nanoseconds]], sign × duration.[[Hours]], sign × duration.[[Minutes]],
        //    sign × duration.[[Seconds]], sign × duration.[[Milliseconds]], sign × duration.[[Microseconds]],
        //    sign × duration.[[Nanoseconds]]).
        val ns = addInstant(
            instant[Slot.Nanoseconds],
            sign * duration.hours,
            sign * duration.minutes,
            sign * duration.seconds,
            sign * duration.milliseconds,
            sign * duration.microseconds,
            sign.toBigInteger() * duration.nanoseconds,
        )

        // 8. Return ! CreateTemporalInstant(ns).
        return createTemporalInstant(ns)
    }

    @JvmStatic
    @ECMAImpl("11.6.8")
    fun parseTimeZoneOffsetString(offsetString: String): Long {
        // 1. Let parseResult be ParseText(StringToCodePoints(offsetString), TimeZoneNumericUTCOffset).
        // 2. If parseResult is a List of errors, throw a RangeError exception.
        // 3. Let each of sign, hours, minutes, seconds, and fSeconds be the source text matched by the respective
        //    TimeZoneUTCOffsetSign, TimeZoneUTCOffsetHour, TimeZoneUTCOffsetMinute, TimeZoneUTCOffsetSecond, and
        //    TimeZoneUTCOffsetFraction Parse Node contained within parseResult, or an empty sequence of code points if
        //    not present.
        val match = Regex.offset.find(offsetString)
            ?: Errors.Temporal.InvalidOffset(offsetString).throwRangeError(realm)

        val groups = match.groups

        val sign = groups[1]!!.value
        val hours = groups[2]!!.value
        val minutes = groups[3]?.value ?: ""
        val seconds = groups[4]?.value ?: ""
        val fSeconds = groups[5]?.value ?: ""

        // 4. Assert: sign is not empty.
        ecmaAssert(sign.isNotEmpty())

        // 5. If sign contains the code point U+002D (HYPHEN-MINUS) or U+2212 (MINUS SIGN), then
        val factor = if ('-' in sign || '\u2212' in sign) {
            // a. Let factor be -1.
            -1
        }
        // 6. Else,
        else {
            // a. Let factor be 1.
            1
        }

        // 7. Assert: hours is not empty.
        ecmaAssert(hours.isNotEmpty())

        // Note: For the following steps, ToIntegerOrInfinity can be replaced with toInt() where it is asserted that the
        //       string is not empty. If that is not asserted, then we can use (.toIntOrNull() ?: 0), as
        //       ToIntegerOrInfinity returns zero for the empty string.

        // 8. Let hoursMV be ! ToIntegerOrInfinity(CodePointsToString(hours)).
        val hoursMV = hours.toInt()

        // 9. Let minutesMV be ! ToIntegerOrInfinity(CodePointsToString(minutes)).
        val minutesMV = minutes.toIntOrNull() ?: 0

        // 10. Let secondsMV be ! ToIntegerOrInfinity(CodePointsToString(seconds)).
        val secondsMV = seconds.toIntOrNull() ?: 0

        // 11. If fSeconds is not empty, then
        val nanosecondsMV = if (fSeconds.isNotEmpty()) {
            // a. Let fSecondsDigits be the substring of CodePointsToString(fSeconds) from 1.
            val fSecondsDigits = fSeconds.substring(1)

            // b. Let fSecondsDigitsExtended be the string-concatenation of fSecondsDigits and "000000000".
            val fSecondsDigitsExtended = fSecondsDigits + "000000000"

            // c. Let nanosecondsDigits be the substring of fSecondsDigitsExtended from 0 to 9.
            val nanosecondsDigits = fSecondsDigitsExtended.substring(0, 9)

            // d. Let nanosecondsMV be ! ToIntegerOrInfinity(nanosecondsDigits).
            nanosecondsDigits.toInt()
        }
        // 12. Else,
        else {
            // a. Let nanosecondsMV be 0.
            0
        }

        // 13. Return factor × (((hoursMV × 60 + minutesMV) × 60 + secondsMV) × 10^9 + nanosecondsMV).
        return factor * (((hoursMV * 60L + minutesMV) * 60L + secondsMV) * 1e9.toLong() + nanosecondsMV)
    }

    @JvmStatic
    @ECMAImpl("12.1.30")
    fun isISOLeapYear(year: Int): Boolean {
        if (year % 4 != 0)
            return false
        if (year % 400 == 0)
            return true
        if (year % 100 == 0)
            return false
        return true
    }

    @JvmStatic
    @ECMAImpl("12.1.32")
    fun isoDaysInMonth(year: Int, month: Int): Int {
        if (month in setOf(1, 3, 5, 7, 8, 10, 12))
            return 31
        if (month != 2)
            return 30
        return if (isISOLeapYear(year)) 29 else 28
    }

    @JvmStatic
    @ECMAImpl("13.2")
    fun getOptionsObject(options: JSValue): JSObject {
        // 1. If options is undefined, then
        if (options == JSUndefined) {
            // a. Return OrdinaryObjectCreate(null).
            return JSObject.create()
        }

        // 2. If Type(options) is Object, then
        if (options is JSObject) {
            // a. Return options.
            return options
        }

        // 3. Throw a TypeError exception.
        Errors.Temporal.OptionsMustBeObject.throwTypeError()
    }

    sealed interface TemporalUnitDefault {
        object Required : TemporalUnitDefault

        class Value(val value: JSValue) : TemporalUnitDefault
    }

    @JvmStatic
    @ECMAImpl("13.3")
    fun getOption(options: JSObject, property: PropertyKey, type: JSValue.Type, values: List<JSValue>?, default: TemporalUnitDefault): JSValue {
        // 1. Let value be ? Get(options, property).
        var value = options.get(property)

        // 2. If value is undefined, then
        if (value == JSUndefined) {
            // a. If default is required, throw a RangeError exception.
            if (default == TemporalUnitDefault.Required)
                Errors.TODO("getOption 1").throwRangeError()

            // b. Return default.
            return (default as TemporalUnitDefault.Value).value
        }

        // 3. If type is "boolean", then
        if (type == JSValue.Type.Boolean)  {
            // a. Set value to ToBoolean(value).
            value = value.toBoolean().toValue()
        }
        // 4. Else if type is "number", then
        else if (type == JSValue.Type.Number) {
            // a. Set value to ? ToNumber(value).
            value = value.toNumber()

            // b. If value is NaN, throw a RangeError exception.
            if (value.isNaN)
                Errors.TODO("getOption 2").throwRangeError()
        }
        // 5. Else,
        else {
            // a. Assert: type is "string".
            ecmaAssert(type == JSValue.Type.String)

            // b. Set value to ? ToString(value).
            value = value.toJSString()
        }

        // 6. If values is not undefined and values does not contain an element equal to value, throw a RangeError exception.
        // TODO: SameValue?
        if (values != null && value !in values)
            Errors.TODO("getOption 3").throwRangeError()

        // 7. Return value.
        return value
    }

    @JvmStatic
    @ECMAImpl("13.6")
    fun toTemporalRoundingMode(normalizedOptions: JSObject, fallback: String): String {
        // 1. Return ? GetOption(normalizedOptions, "roundingMode", "string", « "ceil", "floor", "expand", "trunc", "halfCeil", "halfFloor", "halfExpand", "halfTrunc", "halfEven" », fallback).
        val option = getOption(
            normalizedOptions, 
            "roundingMode".key(), 
            JSValue.Type.String, 
            listOf(
                "ceil".toValue(), 
                "floor".toValue(), 
                "expand".toValue(), 
                "trunc".toValue(), 
                "halfCeil".toValue(), 
                "halfFloor".toValue(), 
                "halfExpand".toValue(), 
                "halfTrunc".toValue(), 
                "halfEvan".toValue(),
            ), 
            TemporalUnitDefault.Value(fallback.toValue()),
        )!!
        expect(option is JSString)
        return option.string
    }

    @JvmStatic
    @ECMAImpl("13.7")
    fun negateTemporalRoundingMode(roundingMode: String): String {
        return when (roundingMode) {
            // 1. If roundingMode is "ceil", return "floor".
            "ceil" -> "floor"

            // 2. If roundingMode is "floor", return "ceil".
            "floor" -> "ceil"

            // 3. If roundingMode is "halfCeil", return "halfFloor".
            "halfCeil" -> "halfFloor" 

            // 4. If roundingMode is "halfFloor", return "halfCeil".
            "halfFloor" -> "halfCeil"

            // 5. Return roundingMode.
            else -> roundingMode
        }
    }

    @JvmStatic
    @ECMAImpl("13.14")
    fun toTemporalRoundingIncrement(normalizedOptions: JSObject, dividend: Int?, inclusive: Boolean): Int {
        val maximum = when {
            // 1. If dividend is undefined, then
            //    a. Let maximum be +∞𝔽.
            dividend == null -> Double.POSITIVE_INFINITY

            // 2. Else if inclusive is true, then
            //    a. Let maximum be 𝔽(dividend).
            inclusive -> dividend.toDouble()

            // 3. Else if dividend is more than 1, then
            //    a. Let maximum be 𝔽(dividend - 1).
            dividend > 1 -> dividend.toDouble() - 1.0

            // 4. Else,
            //    a. Let maximum be 1𝔽.
            else -> 1.0
        }

        // 5. Let increment be ? GetOption(normalizedOptions, "roundingIncrement", "number", undefined, 1𝔽).
        val incrementValue = getOption(
            normalizedOptions,
            "roundingIncrement".key(),
            JSValue.Type.Number,
            null,
            TemporalUnitDefault.Value(1.toValue()),
        )!!
        expect(incrementValue is JSNumber)
        var increment = incrementValue.number

        // 6. If increment < 1𝔽 or increment > maximum, throw a RangeError exception.
        if (increment < 1.0 || increment > maximum)
            Errors.TODO("toTemporalRoundingIncrement 1").throwRangeError(realm)

        // 7. Set increment to floor(ℝ(increment)).
        val inc = floor(increment).toInt()

        // 8. If dividend is not undefined and dividend modulo increment is not zero, then
        if (dividend != null && dividend % inc != 0) {
            // a. Throw a RangeError exception.
            Errors.TODO("toTemporalRoundingIncrement 2").throwRangeError(realm)
        }

        // 9. Return increment.
        return inc
    }

    @JvmStatic
    @ECMAImpl("13.15")
    fun getTemporalUnit(normalizedOptions: JSObject, key: PropertyKey, unitGroup: String, default: TemporalUnitDefault, extraValues: List<String> = emptyList()): String? {
        // 1. Let singularNames be a new empty List.
        val singularNames = mutableListOf<JSValue>()

        // 2. For each row of Table 13, except the header row, in table order, do
        for (temporalUnit in temporalUnits) {
            // a. Let unit be the value in the Singular column of the row.
            val unit = temporalUnit.singular

            // b. If the Category column of the row is date and unitGroup is date or datetime, append unit to singularNames.
            if (!temporalUnit.isTime && (unitGroup == "date" || unitGroup == "datetime")) {
                singularNames.add(unit.toValue())
            }
            // c. Else if the Category column of the row is time and unitGroup is time or datetime, append unit to singularNames.
            else if (temporalUnit.isTime && (unitGroup == "time" || unitGroup == "datetime")) {
                singularNames.add(unit.toValue())
            }
        }

        // 3. If extraValues is present, then
        //    a. Set singularNames to the list-concatenation of singularNames and extraValues.
        singularNames.addAll(extraValues.map(::JSString))
            
        // 4. If default is required, then
        val defaultValue = if (default == TemporalUnitDefault.Required) {
            // a. Let defaultValue be undefined.
            JSUndefined
        }
        // 5. Else,
        else {
            // a. Let defaultValue be default.
            val defaultValue = (default as TemporalUnitDefault.Value).value

            // b. If defaultValue is not undefined and singularNames does not contain defaultValue, then
            // TODO: SameValue?
            if (defaultValue != JSUndefined && defaultValue !in singularNames) {
                // i. Append defaultValue to singularNames.
                singularNames.add(defaultValue)
            }

            defaultValue
        }

        // 6. Let allowedValues be a copy of singularNames.
        val allowedValues = singularNames.toMutableList()

        // 7. For each element singularName of singularNames, do
        for (singularName in singularNames) {
            // a. If singularName is listed in the Singular column of Table 13, then
            val temporalUnit = temporalUnits.firstOrNull { it.singular == (singularName as? JSString)?.string }
            if (temporalUnit != null) {
                // i. Let pluralName be the value in the Plural column of the corresponding row.
                // ii. Append pluralName to allowedValues.
                allowedValues.add(temporalUnit.plural.toValue())
            }
        }

        // 8. NOTE: For each singular Temporal unit name that is contained within allowedValues, the corresponding plural name is also contained within it.

        // 9. Let value be ? GetOption(normalizedOptions, key, "string", allowedValues, defaultValue).
        var value = getOption(normalizedOptions, key, JSValue.Type.String, allowedValues, TemporalUnitDefault.Value(defaultValue))

        // 10. If value is undefined and default is required, throw a RangeError exception.
        if (value == JSUndefined && default == TemporalUnitDefault.Required)
            Errors.TODO("getTemporalUnit").throwRangeError()

        // 11. If value is listed in the Plural column of Table 13, then
        val temporalUnit = temporalUnits.firstOrNull { it.plural == (value as? JSString)?.string }
        if (temporalUnit != null) {
            // a. Set value to the value in the Singular column of the corresponding row.
            value = temporalUnit.singular.toValue()
        }

        // 12. Return value.
        return if (value is JSUndefined) {
            null
        } else value.asString
    }

    data class TemporalUnit(val singular: String, val plural: String, val isTime: Boolean)

    val temporalUnits = mutableListOf(
        TemporalUnit("year", "years", isTime = false),
        TemporalUnit("month", "months", isTime = false),
        TemporalUnit("week", "weeks", isTime = false),
        TemporalUnit("day", "days", isTime = false),
        TemporalUnit("hour", "hours", isTime = true),
        TemporalUnit("minute", "minutes", isTime = true),
        TemporalUnit("second", "seconds", isTime = true),
        TemporalUnit("millisecond", "milliseconds", isTime = true),
        TemporalUnit("microsecond", "microseconds", isTime = true),
        TemporalUnit("nanosecond", "nanoseconds", isTime = true),
    )

    @JvmStatic
    @ECMAImpl("13.17")
    fun largerOfTwoTemporalUnits(unit1: String, unit2: String): String {
        // 1. Assert: Both u1 and u2 are listed in the Singular column of Table 13.
        ecmaAssert(temporalUnits.map { it.singular }.let { unit1 in it && unit2 in it })

        // 2. For each row of Table 13, except the header row, in table order, do
        for (temporalUnit in temporalUnits) {
            // a. Let unit be the value in the Singular column of the row.
            // b. If SameValue(u1, unit) is true, return unit.
            if (temporalUnit.singular == unit1)
                return unit1

            // c. If SameValue(u2, unit) is true, return unit.
            if (temporalUnit.singular == unit2)
                return unit2
        }

        unreachable()
    }

    @JvmStatic
    @ECMAImpl("13.19")
    fun maximumTemporalDurationRoundingIncrement(unit: String): Int? {
        return when (unit) {
            // 1. If unit is "year", "month", "week", or "day", then
            //    a. Return undefined.
            "year", "month", "week", "day" -> null

            // 2. If unit is "hour", then
            //    a. Return 24.
            "hour" -> 24

            // 3. If unit is "minute" or "second", then
            //    a. Return 60.
            "minute", "second" -> 60

            // 4. Assert: unit is one of "millisecond", "microsecond", or "nanosecond".
            // 5. Return 1000.
            "millisecond", "microsecond", "nanosecond" -> 1000
            else -> unreachable()
        }
    }

    @JvmStatic
    @ECMAImpl("13.22")
    fun roundTowardsZero(x: BigDecimal): BigInteger {
        // 1. Return the mathematical value that is the same sign as x and whose magnitude is floor(abs(x)).
        return x.round(MathContext(0, RoundingMode.DOWN)).toBigInteger()
    }

    @JvmStatic
    @ECMAImpl("13.28")
    fun parseISODateTime(isoString: String, zoneRequired: Boolean): ParsedISODate {
        // Note: This implementation is largely derived from the polyfill

        // 1. Let parseResult be empty.
        // 2. For each nonterminal goal of « TemporalDateTimeString, TemporalInstantString, TemporalMonthDayString,
        //    TemporalTimeString, TemporalYearMonthString, TemporalZonedDateTimeString », do
        //    a. If parseResult is not a Parse Node, set parseResult to ParseText(StringToCodePoints(isoString), goal).
        // 3. If parseResult is not a Parse Node, throw a RangeError exception.
        val match = Regex.zonedDateTime.find(isoString) ?: Errors.Temporal.InvalidISO8601String(isoString)
            .throwRangeError(realm)

        // 4. Let each of year, month, day, hour, minute, second, fSeconds, and calendar be the source text matched by
        //    the respective DateYear, DateMonth, DateDay, TimeHour, TimeMinute, TimeSecond, TimeFraction, and
        //    CalendarName Parse Node contained within parseResult, or an empty sequence of code points if not present.
        val groups = match.groups
        var year = groups[1]!!.value
        val month = groups[2]?.value ?: groups[4]!!.value
        val day = groups[3]?.value ?: groups[5]!!.value
        val hour = groups[6]!!.value
        val minute = groups[7]?.value ?: groups[10]!!.value
        val second = groups[8]?.value ?: groups[11]!!.value
        val fSeconds = groups[9]?.value ?: groups[12]!!.value

        // 5. If the first code point of year is U+2212 (MINUS SIGN), replace the first code point with
        //    U+002D (HYPHEN-MINUS).
        if (year[0] == '\u2212')
            year = "-" + year.substring(1)

        // Note that we can use Kotlin's .toInt() for these steps rather than our toIntegerOrInfinity because the
        // regex guarantees that these are valid numbers

        // 6. Let yearMV be ! ToIntegerOrInfinity(CodePointsToString(year)).
        val yearMV = year.toInt()

        // 7. If month is empty, then
        val monthMV = if (month.isEmpty()) {
            // a. Let monthMV be 1.
            1
        }
        // 8. Else,
        else {
            // a. Let monthMV be ! ToIntegerOrInfinity(CodePointsToString(month)).
            month.toInt()
        }

        // 9. If day is empty, then
        val dayMV = if (day.isEmpty()) {
            // a. Let dayMV be 1.
            1
        }
        // 10. Else,
        else {
            // a. Let dayMV be ! ToIntegerOrInfinity(CodePointsToString(day)).
            day.toInt()
        }

        // 11. Let hourMV be ! ToIntegerOrInfinity(CodePointsToString(hour)).
        val hourMV = hour.toInt()

        // 12. Let minuteMV be ! ToIntegerOrInfinity(CodePointsToString(minute)).
        val minuteMV = minute.toInt()

        // 13. Let secondMV be ! ToIntegerOrInfinity(CodePointsToString(second)).
        var secondMV = second.toInt()

        // 14. If secondMV is 60, then
        if (secondMV == 60) {
            // a. Set secondMV to 59.
            secondMV = 59
        }

        var millisecondMV = 0
        var microsecondMV = 0
        var nanosecondMV = 0

        // 15. If fSeconds is not empty, then
        if (fSeconds.isNotEmpty()) {
            // a. Let fSecondsDigits be the substring of CodePointsToString(fSeconds) from 1.
            val fSecondsDigits = fSeconds.substring(1)

            // b. Let fSecondsDigitsExtended be the string-concatenation of fSecondsDigits and "000000000".
            val fSecondsDigitsExtended = fSecondsDigits + "000000000"

            // c. Let millisecond be the substring of fSecondsDigitsExtended from 0 to 3.
            // d. Let microsecond be the substring of fSecondsDigitsExtended from 3 to 6.
            // e. Let nanosecond be the substring of fSecondsDigitsExtended from 6 to 9.

            // f. Let millisecondMV be ! ToIntegerOrInfinity(millisecond).
            millisecondMV = fSecondsDigitsExtended.substring(0, 3).toInt()

            // g. Let microsecondMV be ! ToIntegerOrInfinity(microsecond).
            microsecondMV = fSecondsDigitsExtended.substring(3, 6).toInt()

            // h. Let nanosecondMV be ! ToIntegerOrInfinity(nanosecond).
            nanosecondMV = fSecondsDigitsExtended.substring(6, 9).toInt()
        }
        // 16. Else,
        //     a. Let millisecondMV be 0.
        //     b. Let microsecondMV be 0.
        //     c. Let nanosecondMV be 0.

        // 17. If IsValidISODate(yearMV, monthMV, dayMV) is false, throw a RangeError exception.
        if (!isValidISODate(yearMV, monthMV, dayMV)) {
            val dateString = LocalDate.of(yearMV, monthMV, dayMV).format(DateTimeFormatter.ISO_DATE)
            Errors.Temporal.InvalidISODate(dateString).throwRangeError(realm)
        }

        // 18. If IsValidTime(hourMV, minuteMV, secondMV, millisecondMV, microsecondMV, nanosecondMV) is false, throw a
        //     RangeError exception.
        if (!isValidTime(hourMV, minuteMV, secondMV, millisecondMV, microsecondMV, nanosecondMV.toBigInteger())) {
            val nanoOfSecond = millisecondMV * 1_000_000 + microsecondMV * 1_000 + nanosecondMV
            val timeString = LocalTime.of(hourMV, minuteMV, secondMV, nanoOfSecond).format(DateTimeFormatter.ISO_TIME)
            Errors.Temporal.InvalidISOTime(timeString).throwRangeError(realm)
        }

        // 19. Let timeZoneResult be the Record { [[Z]]: false, [[OffsetString]]: undefined, [[Name]]: undefined }.
        val timeZoneResult = TimeZoneRecord(z = false, offsetString = null, name = null)

        // 20. If parseResult contains a TimeZoneIdentifier Parse Node, then
        val timeZoneIdentifier = groups[19]?.value
        if (timeZoneIdentifier != null) {
            // a. Let name be the source text matched by the TimeZoneIdentifier Parse Node contained within parseResult.
            // b. Set timeZoneResult.[[Name]] to CodePointsToString(name).
            timeZoneResult.name = timeZoneIdentifier
        }

        // 21. If parseResult contains a UTCDesignator Parse Node, then
        if (groups[13] != null) { // ([zZ])
            // a. Set timeZoneResult.[[Z]] to true.
            timeZoneResult.z = true
        }
        // 22. Else,
        else {
            // a. If parseResult contains a TimeZoneNumericUTCOffset Parse Node, then
            if (groups[14] != null && groups[15] != null) {
                // i. Let offset be the source text matched by the TimeZoneNumericUTCOffset Parse Node contained within
                //    parseResult.
                // ii. Set timeZoneResult.[[OffsetString]] to CodePointsToString(offset).

                val offsetSign = groups[14]!!.value.let {
                    if (it == "\u2212" || it == "-") '-' else '+'
                }

                val offsetHours = groups[15]?.value ?: "00"
                val offsetMinutes = groups[16]?.value ?: "00"
                val offsetSeconds = groups[17]?.value ?: "00"
                val offsetFraction = groups[18]?.value ?: "0"

                var offset = "$offsetSign$offsetHours:$offsetMinutes"

                if (offsetFraction.any { it != '0' }) {
                    offset += ":$offsetSeconds.${offsetFraction.dropLastWhile { it == '0' }}"
                } else if (offsetSeconds.any { it != '0' }) {
                    offset += ":$offsetSeconds"
                }

                if (offset == "-00:00")
                    offset = "+00:00"

                timeZoneResult.offsetString = offset
            }
        }

        // 23. If calendar is empty, then
        //     a. Let calendarVal be undefined.
        // 24. Else,
        //     a. Let calendarVal be CodePointsToString(calendar).
        val calendar = groups[20]?.value

        // 25. Return the Record { [[Year]]: yearMV, [[Month]]: monthMV, [[Day]]: dayMV, [[Hour]]: hourMV,
        //     [[Minute]]: minuteMV, [[Second]]: secondMV, [[Millisecond]]: millisecondMV,
        //     [[Microsecond]]: microsecondMV, [[Nanosecond]]: nanosecondMV, [[TimeZone]]: timeZoneResult,
        //     [[Calendar]]: calendarVal, }.
        return ParsedISODate(
            yearMV,
            monthMV,
            dayMV,
            hourMV,
            minuteMV,
            secondMV,
            millisecondMV,
            microsecondMV,
            nanosecondMV.toBigInteger(),
            timeZoneResult.name,
            timeZoneResult.offsetString,
            timeZoneResult.z,
            calendar,
        )
    }

    @JvmStatic
    @ECMAImpl("13.29")
    fun parseTemporalInstantString(isoString: String): ParsedISODate {
        return parseISODateTime(isoString, zoneRequired = true)
    }

    @JvmStatic
    @ECMAImpl("13.34")
    fun parseTemporalDurationString(isoString: String): DurationRecord {
        // 1. Let duration be ParseText(StringToCodePoints(isoString), TemporalDurationString).
        // 2. If duration is a List of errors, throw a RangeError exception.
        // 3. Let each of sign, years, months, weeks, days, hours, fHours, minutes, fMinutes, seconds, and fSeconds be
        //    the source text matched by the respective Sign, DurationYears, DurationMonths, DurationWeeks,
        //    DurationDays, DurationWholeHours, DurationHoursFraction, DurationWholeMinutes, DurationMinutesFraction,
        //    DurationWholeSeconds, and DurationSecondsFraction Parse Node contained within duration, or an empty
        //    sequence of code points if not present.
        val groups = Regex.duration.find(isoString)?.groups
            ?: Errors.Temporal.InvalidDuration(isoString).throwRangeError(realm)

        if (groups.drop(2).any { it == null })
            Errors.Temporal.InvalidDuration(isoString).throwRangeError(realm)

        // 4. Let yearsMV be ! ToIntegerOrInfinity(CodePointsToString(years)).
        // 5. Let monthsMV be ! ToIntegerOrInfinity(CodePointsToString(months)).
        // 6. Let weeksMV be ! ToIntegerOrInfinity(CodePointsToString(weeks)).
        // 7. Let daysMV be ! ToIntegerOrInfinity(CodePointsToString(days)).
        // 8. Let hoursMV be ! ToIntegerOrInfinity(CodePointsToString(hours)).

        val sign = groups[1]!!.value
        val yearsMV = groups[2]!!.value.toInt()
        val monthsMV = groups[3]!!.value.toInt()
        val weeksMV = groups[4]!!.value.toInt()
        val daysMV = groups[5]!!.value.toInt()
        val hoursMV = groups[6]!!.value.toInt()

        val fHours = groups[7]?.value
        val minutes = groups[8]?.value
        val fMinutes = groups[9]?.value
        val seconds = groups[10]?.value
        val fSeconds = groups[11]?.value

        // 9. If fHours is not empty, then
        val minutesMV = if (fHours != null) {
            // a. If any of minutes, fMinutes, seconds, fSeconds is not empty, throw a RangeError exception.
            if (minutes != null || fMinutes != null || seconds != null || fSeconds != null)
                Errors.TODO("parseTemporalDurationString 1").throwRangeError()

            // b. Let fHoursDigits be the substring of CodePointsToString(fHours) from 1.
            val fHoursDigits = fHours.substring(1)

            // c. Let fHoursScale be the length of fHoursDigits.
            val fHoursScale = fHoursDigits.length

            // d. Let minutesMV be ! ToIntegerOrInfinity(fHoursDigits) / 10^fHoursScale × 60.
            fHoursDigits.toInt() / 10.0.pow(fHoursScale) * 60.0
        }
        // 10. Else,
        else {
            // a. Let minutesMV be ! ToIntegerOrInfinity(CodePointsToString(minutes)).
            minutes?.toInt()?.toDouble() ?: 0.0
        }

        // 11. If fMinutes is not empty, then
        val secondsMV = if (fMinutes != null) {
            // a. If any of seconds, fSeconds is not empty, throw a RangeError exception.
            if (seconds != null || fSeconds != null)
                Errors.TODO("parseTemporalDurationString 2").throwRangeError()

            // b. Let fMinutesDigits be the substring of CodePointsToString(fMinutes) from 1.
            val fMinutesDigits = fMinutes.substring(1)

            // c. Let fMinutesScale be the length of fMinutesDigits.
            val fMinutesScale = fMinutesDigits.length

            // d. Let secondsMV be ! ToIntegerOrInfinity(fMinutesDigits) / 10^fMinutesScale × 60.
            fMinutesDigits.toInt() / 10.0.pow(fMinutesScale) * 60.0
        }
        // 12. Else if seconds is not empty, then
        else if (seconds != null) {
            // a. Let secondsMV be ! ToIntegerOrInfinity(CodePointsToString(seconds)).
            seconds.toInt().toDouble()
        }
        // 13. Else,
        else {
            // a. Let secondsMV be remainder(minutesMV, 1) × 60.
            minutesMV.rem(1.0) * 60.0
        }

        // 14. If fSeconds is not empty, then
        val millisecondsMV = if (fSeconds != null) {
            // a. Let fSecondsDigits be the substring of CodePointsToString(fSeconds) from 1.
            val fSecondsDigits = fSeconds.substring(1)

            // b. Let fSecondsScale be the length of fSecondsDigits.
            val fSecondsScale = fSecondsDigits.length

            // c. Let millisecondsMV be ! ToIntegerOrInfinity(fSecondsDigits) / 10^fSecondsScale × 1000.
            fSecondsDigits.toInt() / 10.0.pow(fSecondsScale) * 1000.0
        }
        // 15. Else,
        else {
            // a. Let millisecondsMV be remainder(secondsMV, 1) × 1000.
            secondsMV.rem(1.0) * 1000.0
        }

        // 16. Let microsecondsMV be remainder(millisecondsMV, 1) × 1000.
        val microsecondsMV = millisecondsMV.rem(1.0) * 1000

        // 17. Let nanosecondsMV be remainder(microsecondsMV, 1) × 1000.
        val nanosecondsMV = microsecondsMV.rem(1.0) * 1000

        // 18. If sign contains the code point U+002D (HYPHEN-MINUS) or U+2212 (MINUS SIGN), then
        val factor = if ('-' in sign || '\u2212' in sign) {
            // a. Let factor be -1.
            -1
        }
        // 19. Else,
        else {
            // a. Let factor be 1.
            1
        }

        // 20. Return ? CreateDurationRecord(yearsMV × factor, monthsMV × factor, weeksMV × factor, daysMV × factor,
        //     hoursMV × factor, floor(minutesMV) × factor, floor(secondsMV) × factor, floor(millisecondsMV) × factor,
        //     floor(microsecondsMV) × factor, floor(nanosecondsMV) × factor).
        return createDurationRecord(
            yearsMV * factor,
            monthsMV * factor,
            weeksMV * factor,
            daysMV * factor,
            hoursMV * factor,
            floor(minutesMV).toInt() * factor,
            floor(secondsMV).toInt() * factor,
            floor(millisecondsMV).toInt() * factor,
            floor(microsecondsMV).toInt() * factor,
            (floor(nanosecondsMV).toInt() * factor).toBigInteger(),
        )
    }

    @JvmStatic
    @ECMAImpl("13.42")
    fun toIntegerWithoutRounding(argument: JSValue): Int {
        // 1. Let number be ? ToNumber(argument).
        val number = argument.toNumber()

        // 2. If number is NaN, +0𝔽, or -0𝔽, return 0.
        if (number.isNaN || number.isZero)
            return 0

        // 3. If IsIntegralNumber(number) is false, throw a RangeError exception.
        if (AOs.isIntegralNumber(number))
            Errors.TODO("toIntegerWithoutRounding").throwRangeError()

        // 4. Return ℝ(number).
        return number.asInt
    }

    @JvmStatic
    @ECMAImpl("13.44")
    fun getDifferenceSettings(
        isUntil: Boolean,
        options: JSValue,
        unitGroup: String,
        disallowedUnits: List<String>,
        fallbackSmallestUnit: String,
        smallestLargestDefaultUnit: String,
    ): DifferenceSettingsRecord {
        // 1. Set options to ? GetOptionsObject(options).
        val options = getOptionsObject(options)

        // 2. Let smallestUnit be ? GetTemporalUnit(options, "smallestUnit", unitGroup, fallbackSmallestUnit).
        val smallestUnit = getTemporalUnit(options, "smallestUnit".key(), unitGroup, TemporalUnitDefault.Value(fallbackSmallestUnit.toValue()))!!

        // 3. If disallowedUnits contains smallestUnit, throw a RangeError exception.
        if (smallestUnit in disallowedUnits)
            Errors.TODO("getDifferenceSettings 1").throwRangeError()
            
        // 4. Let defaultLargestUnit be ! LargerOfTwoTemporalUnits(smallestLargestDefaultUnit, smallestUnit).
        val defaultLargestUnit = largerOfTwoTemporalUnits(smallestLargestDefaultUnit, smallestUnit)
        
        // 5. Let largestUnit be ? GetTemporalUnit(options, "largestUnit", unitGroup, "auto").
        var largestUnit = getTemporalUnit(options, "largestUnit".key(), unitGroup, TemporalUnitDefault.Value("auto".toValue()))!!
            
        // 6. If disallowedUnits contains largestUnit, throw a RangeError exception.
        if (largestUnit in disallowedUnits)
            Errors.TODO("getDifferenceSettings 2").throwRangeError()

        // 7. If largestUnit is "auto", set largestUnit to defaultLargestUnit.
        if (largestUnit == "auto")
            largestUnit = defaultLargestUnit

        // 8. If LargerOfTwoTemporalUnits(largestUnit, smallestUnit) is not largestUnit, throw a RangeError exception.
        if (largerOfTwoTemporalUnits(largestUnit, smallestUnit) != largestUnit)
            Errors.TODO("getDifferenceSettings 3").throwRangeError()

        // 9. Let roundingMode be ? ToTemporalRoundingMode(options, "trunc").
        var roundingMode = toTemporalRoundingMode(options, "trunc")

        // 10. If operation is since, then
        if (!isUntil) {
            // a. Set roundingMode to ! NegateTemporalRoundingMode(roundingMode).
            roundingMode = negateTemporalRoundingMode(roundingMode)
        }

        // 11. Let maximum be ! MaximumTemporalDurationRoundingIncrement(smallestUnit).
        val maximum = maximumTemporalDurationRoundingIncrement(smallestUnit)

        // 12. Let roundingIncrement be ? ToTemporalRoundingIncrement(options, maximum, false).
        val roundingIncrement = toTemporalRoundingIncrement(options, maximum, false)

        // 13. Return the Record { [[SmallestUnit]]: smallestUnit, [[LargestUnit]]: largestUnit, [[RoundingMode]]: roundingMode, [[RoundingIncrement]]: roundingIncrement, [[Options]]: options }.
        return DifferenceSettingsRecord(smallestUnit, largestUnit, roundingMode, roundingIncrement, options)
    }

    data class DifferenceSettingsRecord(
        val smallestUnit: String,
        val largestUnit: String,
        val roundingMode: String,
        val roundingIncrement: Int,
        val options: JSValue,
    )

    data class TimeZoneRecord(var z: Boolean, var offsetString: String?, var name: String?)

    data class DurationRecord(
        var years: Int,
        var months: Int,
        var weeks: Int,
        var days: Int,
        var hours: Int,
        var minutes: Int,
        var seconds: Int,
        var milliseconds: Int,
        var microseconds: Int,
        var nanoseconds: BigInteger,
    )

    data class PartialDurationRecord(
        var years: Int? = null,
        var months: Int? = null,
        var weeks: Int? = null,
        var days: Int? = null,
        var hours: Int? = null,
        var minutes: Int? = null,
        var seconds: Int? = null,
        var milliseconds: Int? = null,
        var microseconds: Int? = null,
        var nanoseconds: BigInteger? = null,
    ) {
        fun toDurationRecord() = DurationRecord(
            years ?: 0,
            months ?: 0,
            weeks ?: 0,
            days ?: 0,
            hours ?: 0,
            minutes ?: 0,
            seconds ?: 0,
            milliseconds ?: 0,
            microseconds ?: 0,
            nanoseconds ?: BigInteger.ZERO,
        )
    }

    data class ParsedISODate(
        val year: Int,
        val month: Int,
        val day: Int,
        val hour: Int,
        val minute: Int,
        val second: Int,
        val millisecond: Int,
        val microsecond: Int,
        val nanosecond: BigInteger,
        val name: String?,
        val offset: String?,
        val z: Boolean,
        val calendar: String?,
    )

    // https://github.com/js-temporal/temporal-polyfill/blob/5a55047e5328bb0f124ec9264bb4b6c6ef94f7eb/lib/regex.mjs
    object RegexStrings {
        const val tzComponent =
            """\.[-A-Za-z_]|\.\.[-A-Za-z._]{1,12}|\.[-A-Za-z_][-A-Za-z._]{0,12}|[A-Za-z_][-A-Za-z._]{0,13}"""
        const val offsetNoCapture = """(?:[+\u2212-][0-2][0-9](?::?[0-5][0-9](?::?[0-5][0-9](?:[.,]\d{1,9})?)?)?)"""
        const val timeZoneID =
            """(?:(?:$tzComponent)(?:/(?:$tzComponent))*|Etc/GMT[-+]\d{1,2}|EST5EDT|CST6CDT|MST7MST|PST8PDT|$offsetNoCapture)"""
        const val calComponent = """[A-Za-z0-9]{3,8}"""
        const val calendarID = """(?:$calComponent(?:-$calComponent)*)"""
        const val yearPart = """(?:[+\u2212-]\d{6}|\d{4})"""
        const val monthPart = """(?:0[1-9]|1[0-2])"""
        const val dayPart = """(?:0[1-9]|[12]\d|3[01])"""
        const val dateSplit = """($yearPart)(?:-($monthPart)-($dayPart)|($monthPart)($dayPart))"""
        const val timeSplit =
            """(\d{2})(?::(\d{2})(?::(\d{2})(?:[.,](\d{1,9}))?)?|(\d{2})(?:(\d{2})(?:[.,](\d{1,9}))?)?)?"""
        const val offset = """([+\u2212-])([01][0-9]|2[0-3])(?::?([0-5][0-9])(?::?([0-5][0-9])(?:[.,](\d{1,9}))?)?)?"""
        const val zoneSplit = """(?:([zZ])|(?:$offset)?)(?:\[($timeZoneID)])?"""
        const val calendar = """\[u-ca=($calendarID)]"""
        const val zonedDateTime = """^$dateSplit(?:(?:T|\s+)$timeSplit)?$zoneSplit(?:$calendar)?$"""
        const val time = """^T?$timeSplit(?:$zoneSplit)?(?:$calendar)?$"""
        const val yearMonth = """^($yearPart)-?($monthPart)$"""
        const val monthDay = """^(?:--)?($monthPart)-?($dayPart)$"""
        const val fraction = """(\d+)(?:[.,](\d{1,9}))?"""
        const val durationDate = """(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)W)?(?:(\d+)D)?"""
        const val durationTime = """(?:${fraction}H)?(?:${fraction}M)?(?:${fraction}S)?"""
        const val duration = """^([+\u2212-])?P$durationDate(?:T(?!$)$durationTime)?$"""
    }

    object Regex {
        val timeZoneID = RegexStrings.timeZoneID.toRegex()
        val calendarID = RegexStrings.calendarID.toRegex()
        val dateSplit = RegexStrings.dateSplit.toRegex()
        val offset = RegexStrings.offset.toRegex()
        val zonedDateTime = RegexStrings.zonedDateTime.toRegex(RegexOption.IGNORE_CASE)
        val time = RegexStrings.time.toRegex(RegexOption.IGNORE_CASE)
        val yearMonth = RegexStrings.yearMonth.toRegex()
        val monthDay = RegexStrings.monthDay.toRegex()
        val duration = RegexStrings.duration.toRegex(RegexOption.IGNORE_CASE)
    }
}
