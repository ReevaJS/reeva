package com.reevajs.reeva.runtime.temporal

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.PropertyKey
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.JSNumber
import com.reevajs.reeva.runtime.primitives.JSString
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.utils.*
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
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
    @ECMAImpl("3.5.3")
    fun createTemporalDate(isoYear: Int, isoMonth: Int, isoDay: Int, calendar: JSObject, newTarget: JSObject? = null): JSObject {
        TODO()
    }

    @JvmStatic
    @ECMAImpl("3.5.4")
    fun toTemporalDate(item: JSValue, options: JSObject? = null): JSObject {
        TODO()
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
    @ECMAImpl("5.5.3")
    fun interpretTemporalDateTimeFields(calendar: JSObject, fields: JSObject, options: JSObject): DateTimeRecord {
        TODO()
    }

    @JvmStatic
    @ECMAImpl("5.5.6")
    fun createTemporalDateTime(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, millisecond: Int, microsecond: Int, nanosecond: BigInteger, calendar: JSObject, newTarget: JSValue? = null): JSObject {
        TODO()
    }

    @JvmStatic
    @ECMAImpl("6.5.1")
    fun interpretISODateTimeOffset(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, millisecond: Int, microsecond: Int, nanosecond: BigInteger, offsetBehaviour: String, offsetNanoseconds: BigInteger, timeZone: JSObject, disambiguation: String, offsetOption: String, matchBehavior: String): BigInteger {
        // 1. Let calendar be ! GetISO8601Calendar().
        val calendar = getISO8601Calendar()

        // 2. Let dateTime be ? CreateTemporalDateTime(year, month, day, hour, minute, second, millisecond, microsecond, nanosecond, calendar).
        val dateTime = createTemporalDateTime(year, month, day, hour, minute, second, millisecond, microsecond, nanosecond, calendar)

        // 3. If offsetBehaviour is wall or offsetOption is "ignore", then
        if (offsetBehaviour == "wall" || offsetOption == "ignore") {
            // a. Let instant be ? BuiltinTimeZoneGetInstantFor(timeZone, dateTime, disambiguation).
            val instant = builtinTimeZoneGetInstantFor(timeZone, dateTime, disambiguation)

            // b. Return instant.[[Nanoseconds]].
            return instant[Slot.Nanoseconds]
        }

        // 4. If offsetBehaviour is exact or offsetOption is "use", then
        if (offsetBehaviour == "exact" || offsetOption == "use") {
            // a. Let epochNanoseconds be GetEpochFromISOParts(year, month, day, hour, minute, second, millisecond, microsecond, nanosecond).
            var epochNanoseconds = getEpochFromISOParts(year, month, day, hour, minute, second, millisecond, microsecond, nanosecond)

            // b. Set epochNanoseconds to epochNanoseconds - ℤ(offsetNanoseconds).
            epochNanoseconds -= offsetNanoseconds

            // c. If ! IsValidEpochNanoseconds(epochNanoseconds) is false, throw a RangeError exception.
            if (!isValidEpochNanoseconds(epochNanoseconds))
                Errors.TODO("interpretISODateTimeOffset 1").throwRangeError()

            // d. Return epochNanoseconds.
            return epochNanoseconds
        }

        // 5. Assert: offsetBehaviour is option.
        ecmaAssert(offsetBehaviour == "option")

        // 6. Assert: offsetOption is "prefer" or "reject".
        ecmaAssert(offsetOption == "prefer" || offsetOption == "reject")

        // 7. Let possibleInstants be ? GetPossibleInstantsFor(timeZone, dateTime).
        val possibleInstants = getPossibleInstantsFor(timeZone, dateTime)

        // 8. For each element candidate of possibleInstants, do
        for (candidate in possibleInstants) {
            // a. Let candidateNanoseconds be ? GetOffsetNanosecondsFor(timeZone, candidate).
            val candidateNanoseconds = getOffsetNanosecondsFor(timeZone, candidate)

            // b. If candidateNanoseconds = offsetNanoseconds, then
            if (candidateNanoseconds == offsetNanoseconds) {
                // i. Return candidate.[[Nanoseconds]].
                return candidate[Slot.Nanoseconds]
            }

            // c. If matchBehaviour is match minutes, then
            if (matchBehavior == "match minutes") {
                // i. Let roundedCandidateNanoseconds be RoundNumberToIncrement(candidateNanoseconds, 60 × 10^9, "halfExpand").
                val roundedCandidateNanoseconds = roundNumberToIncrement(candidateNanoseconds, BigInteger.valueOf(60L) * BigInteger.TEN.pow(9), "halfExpand")

                // ii. If roundedCandidateNanoseconds = offsetNanoseconds, then
                if (roundedCandidateNanoseconds == offsetNanoseconds) {
                    // 1. Return candidate.[[Nanoseconds]].
                    return candidate[Slot.Nanoseconds]
                }
            }
        }

        // 9. If offsetOption is "reject", throw a RangeError exception.
        if (offsetOption == "reject")
            Errors.TODO("interpretISODateTimeOffset 2").throwRangeError()

        // 10. Let instant be ? DisambiguatePossibleInstants(possibleInstants, timeZone, dateTime, disambiguation).
        val instant = disambiguatePossibleInstants(possibleInstants, timeZone, dateTime, disambiguation)

        // 11. Return instant.[[Nanoseconds]].
        return instant[Slot.Nanoseconds]
    }

    @JvmStatic
    @ECMAImpl("6.5.3")
    fun createTemporalZonedDateTime(epochNanoseconds: BigInteger, timeZone: JSObject, calendar: JSObject, newTarget: JSObject? = null): JSObject {
        TODO()
    }

    @JvmStatic
    @ECMAImpl("6.5.5")
    fun addZonedDateTime(
        epochNanoseconds: BigInteger, 
        timeZone: JSObject, 
        calendar: JSObject,
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
        options: JSObject? = null,
    ): BigInteger {
        TODO()
    }

    @JvmStatic
    @ECMAImpl("6.5.6")
    fun differenceZonedDateTime(nanosecond1: BigInteger, nanosecond2: BigInteger, timeZone: JSObject, calendar: JSObject, largestUnit: String, options: JSObject): DurationRecord {
        TODO()
    }

    @JvmStatic
    @ECMAImpl("6.5.7")
    fun nanosecondsToDays(nanoseconds: BigInteger, relativeTo: JSValue): NanosecondsToDaysRecord {
        TODO()
    }

    data class NanosecondsToDaysRecord(val days: Int, val nanoseconds: BigInteger, val dayLength: BigInteger)

    @JvmStatic
    @ECMAImpl("7.5.5")
    fun createDurationRecord(duration: DurationRecord): DurationRecord {
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
    ) = createDurationRecord(DurationRecord(
        years, months, weeks, days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds,
    ))

    @JvmStatic
    @ECMAImpl("7.5.6")
    fun createDateDurationRecord(years: Int, months: Int, weeks: Int, days: Int): DateDurationRecord {
        // 1. If ! IsValidDuration(years, months, weeks, days, 0, 0, 0, 0, 0, 0) is false, throw a RangeError exception.
        if (!isValidDuration(DurationRecord(years, months, weeks, days, 0, 0, 0, 0, 0, BigInteger.ZERO)))
            Errors.TODO("createDateDurationRecord").throwRangeError()

        // 2. Return the Record { [[Years]]: ℝ(𝔽(years)), [[Months]]: ℝ(𝔽(months)), [[Weeks]]: ℝ(𝔽(weeks)), [[Days]]: ℝ(𝔽(days)) }.
        return DateDurationRecord(years, months, weeks, days)
    }

    @JvmStatic
    @ECMAImpl("7.5.7")
    fun createTimeDurationRecord(days: Int, hours: Int, minutes: Int, seconds: Int, milliseconds: Int, microseconds: Int, nanoseconds: BigInteger): TimeDurationRecord {
        // 1. If ! IsValidDuration(0, 0, 0, days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds) is false, throw a RangeError exception.
        if (!isValidDuration(DurationRecord(0, 0, 0, days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds)))
            Errors.TODO("createTimeDurationRecord").throwRangeError()

        // 2. Return the Record { [[Days]]: ℝ(𝔽(days)), [[Hours]]: ℝ(𝔽(hours)), [[Minutes]]: ℝ(𝔽(minutes)), [[Seconds]]: ℝ(𝔽(seconds)), [[Milliseconds]]: ℝ(𝔽(milliseconds)), [[Microseconds]]: ℝ(𝔽(microseconds)), [[Nanoseconds]]: ℝ(𝔽(nanoseconds)) }.
        return TimeDurationRecord(days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds)
    }

    @JvmStatic
    @ECMAImpl("7.5.8")
    fun toTemporalDuration(item: JSValue): JSObject {
        // 1. If Type(item) is Object and item has an [[InitializedTemporalDuration]] internal slot, then
        if (item is JSObject && Slot.InitializedTemporalDuration in item) {
            // a. Return item.
            return item
        }


        // 2. Let result be ? ToTemporalDurationRecord(item).
        val result = toTemporalDurationRecord(item)

        // 3. Return ! CreateTemporalDuration(result.[[Years]], result.[[Months]], result.[[Weeks]], result.[[Days]], result.[[Hours]], result.[[Minutes]], result.[[Seconds]], result.[[Milliseconds]], result.[[Microseconds]], result.[[Nanoseconds]]).
        return createTemporalDuration(result)
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
    @ECMAImpl("7.5.12")
    fun defaultTemporalLargestUnit(years: Int, months: Int, weeks: Int, days: Int, hours: Int, minutes: Int, seconds: Int, milliseconds: Int, microseconds: Int): String {
        return when {
            // 1. If years is not zero, return "year".
            years != 0 -> "year"

            // 2. If months is not zero, return "month".
            months != 0 -> "month"

            // 3. If weeks is not zero, return "week".
            weeks != 0 -> "week"

            // 4. If days is not zero, return "day".
            days != 0 -> "day"

            // 5. If hours is not zero, return "hour".
            hours != 0 -> "hour"

            // 6. If minutes is not zero, return "minute".
            minutes != 0 -> "minute"

            // 7. If seconds is not zero, return "second".
            seconds != 0 -> "second"

            // 8. If milliseconds is not zero, return "millisecond".
            milliseconds != 0 -> "millisecond"

            // 9. If microseconds is not zero, return "microsecond".
            microseconds != 0 -> "microsecond"

            // 10. Return "nanosecond".
            else -> "nanosecond"
        }
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
    fun createTemporalDuration(duration: DurationRecord, newTarget: JSObject? = null): JSObject {
        // 1. If ! IsValidDuration(years, months, weeks, days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds) is false, throw a RangeError exception.
        if (!isValidDuration(duration))
            Errors.TODO("createTemporalDuration").throwRangeError()

        // 2. If newTarget is not present, set newTarget to %Temporal.Duration%.
        // 3. Let object be ? OrdinaryCreateFromConstructor(newTarget, "%Temporal.Duration.prototype%", « [[InitializedTemporalDuration]], [[Years]], [[Months]], [[Weeks]], [[Days]], [[Hours]], [[Minutes]], [[Seconds]], [[Milliseconds]], [[Microseconds]], [[Nanoseconds]] »).
        val obj = AOs.ordinaryCreateFromConstructor(
            newTarget ?: realm.durationCtor,
            listOf(Slot.InitializedTemporalDuration),
            defaultProto = Realm::durationProto,
        )

        // 4. Set object.[[Years]] to ℝ(𝔽(years)).
        obj[Slot.Years] = duration.years
        
        // 5. Set object.[[Months]] to ℝ(𝔽(months)).
        obj[Slot.Months] = duration.months

        // 6. Set object.[[Weeks]] to ℝ(𝔽(weeks)).
        obj[Slot.Weeks] = duration.weeks

        // 7. Set object.[[Days]] to ℝ(𝔽(days)).
        obj[Slot.Days] = duration.days

        // 8. Set object.[[Hours]] to ℝ(𝔽(hours)).
        obj[Slot.Hours] = duration.hours

        // 9. Set object.[[Minutes]] to ℝ(𝔽(minutes)).
        obj[Slot.Minutes] = duration.minutes

        // 10. Set object.[[Seconds]] to ℝ(𝔽(seconds)).
        obj[Slot.Seconds] = duration.seconds

        // 11. Set object.[[Milliseconds]] to ℝ(𝔽(milliseconds)).
        obj[Slot.Milliseconds] = duration.milliseconds

        // 12. Set object.[[Microseconds]] to ℝ(𝔽(microseconds)).
        obj[Slot.Microseconds] = duration.microseconds

        // 13. Set object.[[Nanoseconds]] to ℝ(𝔽(nanoseconds)).
        obj[Slot.Nanoseconds] = duration.nanoseconds

        // 14. Return object.
        return obj
    }

    @JvmStatic
    @ECMAImpl("7.5.15")
    fun createNegatedTemporalDuration(duration: JSObject): JSObject {
        // 1. Return ! CreateTemporalDuration(-duration.[[Years]], -duration.[[Months]], -duration.[[Weeks]], -duration.[[Days]], -duration.[[Hours]], -duration.[[Minutes]], -duration.[[Seconds]], -duration.[[Milliseconds]], -duration.[[Microseconds]], -duration.[[Nanoseconds]]).
        return createTemporalDuration(DurationRecord(
            -duration[Slot.Years],
            -duration[Slot.Months],
            -duration[Slot.Weeks],
            -duration[Slot.Days],
            -duration[Slot.Hours],
            -duration[Slot.Minutes],
            -duration[Slot.Seconds],
            -duration[Slot.Milliseconds],
            -duration[Slot.Microseconds],
            -duration[Slot.Nanoseconds],
        ))
    }

    @JvmStatic
    @ECMAImpl("7.5.16")
    fun calculateOffsetShift(relativeTo: JSValue, years: Int, months: Int, weeks: Int, days: Int): BigInteger {
        // 1. If Type(relativeTo) is not Object or relativeTo does not have an [[InitializedTemporalZonedDateTime]] internal slot, return 0.
        if (relativeTo !is JSObject || Slot.InitializedTemporalZonedDateTime !in relativeTo)
            return BigInteger.ZERO

        // 2. Let instant be ! CreateTemporalInstant(relativeTo.[[Nanoseconds]]).
        val instant = createTemporalInstant(relativeTo[Slot.Nanoseconds])

        // 3. Let offsetBefore be ? GetOffsetNanosecondsFor(relativeTo.[[TimeZone]], instant).
        val offsetBefore = getOffsetNanosecondsFor(relativeTo[Slot.TimeZone], instant)

        // 4. Let after be ? AddZonedDateTime(relativeTo.[[Nanoseconds]], relativeTo.[[TimeZone]], relativeTo.[[Calendar]], y, mon, w, d, 0, 0, 0, 0, 0, 0).
        val after = addZonedDateTime(relativeTo[Slot.Nanoseconds], relativeTo[Slot.TimeZone], relativeTo[Slot.Calendar], years, months, weeks, days, 0, 0, 0, 0, 0, BigInteger.ZERO)

        // 5. Let instantAfter be ! CreateTemporalInstant(after).
        val instantAfter = createTemporalInstant(after)

        // 6. Let offsetAfter be ? GetOffsetNanosecondsFor(relativeTo.[[TimeZone]], instantAfter).
        val offsetAfter = getOffsetNanosecondsFor(relativeTo[Slot.TimeZone], instantAfter)

        // 7. Return offsetAfter - offsetBefore.
        return offsetAfter - offsetBefore
    }

    @JvmStatic
    @ECMAImpl("7.5.17")
    fun totalDurationNanoseconds(days: Int, hours_: Int, minutes_: Int, seconds_: Int, milliseconds_: Int, microseconds_: Int, nanoseconds_: BigInteger, offsetShift: BigInteger): BigInteger {
        var hours = hours_
        var minutes = minutes_
        var seconds = seconds_
        var milliseconds = milliseconds_
        var microseconds = microseconds_
        var nanoseconds = nanoseconds_

        // 1. If days ≠ 0, then
        if (days != 0) {
            // a. Set nanoseconds to nanoseconds - offsetShift.
            nanoseconds = nanoseconds - offsetShift
        }

        // 2. Set hours to hours + days × 24.
        hours = hours + days * 24

        // 3. Set minutes to minutes + hours × 60.
        minutes = minutes + hours * 60

        // 4. Set seconds to seconds + minutes × 60.
        seconds = seconds + minutes * 60

        // 5. Set milliseconds to milliseconds + seconds × 1000.
        milliseconds = milliseconds + seconds * 1000

        // 6. Set microseconds to microseconds + milliseconds × 1000.
        microseconds = microseconds + milliseconds * 1000

        // 7. Return nanoseconds + microseconds × 1000.
        return nanoseconds + microseconds.toBigInteger() + BigInteger.valueOf(1000L)
    }

    @JvmStatic
    @ECMAImpl("7.5.18")
    fun balanceDuration(days: Int, hours: Int, minutes: Int, seconds: Int, milliseconds: Int, microseconds: Int, nanoseconds: BigInteger, largestUnit: String, relativeTo: JSValue = JSUndefined): TimeDurationRecord {
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

        class Balanced(val result: TimeDurationRecord) : InfiniteDurationResult()
    }

    @JvmStatic
    @ECMAImpl("7.5.19")
    fun balancePossiblyInfiniteDuration(
        days_: Int, 
        hours_: Int, 
        minutes_: Int, 
        seconds_: Int, 
        milliseconds_: Int, 
        microseconds_: Int, 
        nanoseconds_: BigInteger, 
        largestUnit: String, 
        relativeTo_: JSValue = JSUndefined,
    ): InfiniteDurationResult {
        var days = days_
        var hours = hours_
        var minutes = minutes_
        var seconds = seconds_
        var milliseconds = milliseconds_
        var microseconds = microseconds_
        var nanoseconds = nanoseconds_
        var relativeTo = relativeTo_

        // 1. If relativeTo is not present, set relativeTo to undefined.
        
        // 2. If Type(relativeTo) is Object and relativeTo has an [[InitializedTemporalZonedDateTime]] internal slot, then
        nanoseconds = if (relativeTo is JSObject && Slot.InitializedTemporalZonedDateTime in relativeTo) {
            // a. Let endNs be ? AddZonedDateTime(relativeTo.[[Nanoseconds]], relativeTo.[[TimeZone]], relativeTo.[[Calendar]], 0, 0, 0, days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds).
            val endNs = addZonedDateTime(relativeTo[Slot.Nanoseconds], relativeTo[Slot.TimeZone], relativeTo[Slot.Calendar], 0, 0, 0, days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds)

            // b. Set nanoseconds to ℝ(endNs - relativeTo.[[Nanoseconds]]).
            endNs - relativeTo[Slot.Nanoseconds]
        }
        // 3. Else,
        else {
            // a. Set nanoseconds to ! TotalDurationNanoseconds(days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds, 0).
            totalDurationNanoseconds(days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds, BigInteger.ZERO)
        }

        // 4. If largestUnit is one of "year", "month", "week", or "day", then
        if (largestUnit.let { it == "year" || it == "month" || it == "week" || it == "day" }) {
            // a. Let result be ? NanosecondsToDays(nanoseconds, relativeTo).
            val result = nanosecondsToDays(nanoseconds, relativeTo)

            // b. Set days to result.[[Days]].
            days = result.days

            // c. Set nanoseconds to result.[[Nanoseconds]].
            nanoseconds = result.nanoseconds
        }
        // 5. Else,
        else {
            // a. Set days to 0.
            days = 0
        }

        // 6. Set hours, minutes, seconds, milliseconds, and microseconds to 0.
        hours = 0
        minutes = 0
        seconds = 0
        milliseconds = 0
        microseconds = 0

        // 7. If nanoseconds < 0, let sign be -1; else, let sign be 1.
        val sign = if (nanoseconds < BigInteger.ZERO) -1 else 1

        // 8. Set nanoseconds to abs(nanoseconds).
        nanoseconds = nanoseconds.abs()

        // 9. If largestUnit is "year", "month", "week", "day", or "hour", then
        if (largestUnit.let { it == "year" || it == "month" || it == "week" || it == "day" }) {
            // a. Set microseconds to floor(nanoseconds / 1000).
            microseconds = nanoseconds.divide(BigInteger.valueOf(1000L)).toInt()

            // b. Set nanoseconds to nanoseconds modulo 1000.
            nanoseconds %= BigInteger.valueOf(1000L)

            // c. Set milliseconds to floor(microseconds / 1000).
            milliseconds /= 1000

            // d. Set microseconds to microseconds modulo 1000.
            microseconds %= 1000

            // e. Set seconds to floor(milliseconds / 1000).
            seconds /= 1000

            // f. Set milliseconds to milliseconds modulo 1000.
            milliseconds %= 1000

            // g. Set minutes to floor(seconds / 60).
            minutes /= 60

            // h. Set seconds to seconds modulo 60.
            seconds %= 60

            // i. Set hours to floor(minutes / 60).
            hours /= 60

            // j. Set minutes to minutes modulo 60.
            minutes %= 60
        }
        // 10. Else if largestUnit is "minute", then
        else if (largestUnit == "minute") {
            // a. Set microseconds to floor(nanoseconds / 1000).
            microseconds = nanoseconds.divide(BigInteger.valueOf(1000L)).toInt()

            // b. Set nanoseconds to nanoseconds modulo 1000.
            nanoseconds %= BigInteger.valueOf(1000L)

            // c. Set milliseconds to floor(microseconds / 1000).
            milliseconds /= 1000

            // d. Set microseconds to microseconds modulo 1000.
            microseconds %= 1000

            // e. Set seconds to floor(milliseconds / 1000).
            seconds /= 1000

            // f. Set milliseconds to milliseconds modulo 1000.
            milliseconds %= 1000

            // g. Set minutes to floor(seconds / 60).
            minutes /= 60

            // h. Set seconds to seconds modulo 60.
            seconds %= 60
        }
        // 11. Else if largestUnit is "second", then
        else if (largestUnit == "second") {
            // a. Set microseconds to floor(nanoseconds / 1000)..toInt()
            microseconds = nanoseconds.divide(BigInteger.valueOf(1000L)).toInt()

            // b. Set nanoseconds to nanoseconds modulo 1000.
            nanoseconds %= BigInteger.valueOf(1000L)

            // c. Set milliseconds to floor(microseconds / 1000).
            milliseconds /= 1000

            // d. Set microseconds to microseconds modulo 1000.
            microseconds %= 1000

            // e. Set seconds to floor(milliseconds / 1000).
            seconds /= 1000

            // f. Set milliseconds to milliseconds modulo 1000.
            milliseconds %= 1000
        }
        // 12. Else if largestUnit is "millisecond", then
        else if (largestUnit == "millisecond") {
            // a. Set microseconds to floor(nanoseconds / 1000).
            microseconds = nanoseconds.divide(BigInteger.valueOf(1000L)).toInt()

            // b. Set nanoseconds to nanoseconds modulo 1000.
            nanoseconds %= BigInteger.valueOf(1000L)

            // c. Set milliseconds to floor(microseconds / 1000).
            milliseconds /= 1000

            // d. Set microseconds to microseconds modulo 1000.
            microseconds %= 1000
        }
        // 13. Else if largestUnit is "microsecond", then
        else if (largestUnit == "microsecond") {
            // a. Set microseconds to floor(nanoseconds / 1000).
            microseconds = nanoseconds.divide(BigInteger.valueOf(1000L)).toInt()

            // b. Set nanoseconds to nanoseconds modulo 1000.
            nanoseconds %= BigInteger.valueOf(1000L)
        }
        // 14. Else,
        else {
            // a. Assert: largestUnit is "nanosecond".
            ecmaAssert(largestUnit == "nanosecond")
        }

        // 15. For each value v of « days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds », do
        //     a. If 𝔽(v) is not finite, then
        //        i. If sign = 1, then
        //           1. Return positive overflow.
        //        ii. Else if sign = -1, then
        //           1. Return negative overflow.
        // TODO: It seems wrong to convert the BigDecimal to a double before checking for infinity. Also, how
        //       can the other values be infinite?
        if (!nanoseconds.toBigDecimal().toDouble().isFinite()) {
            return if (sign == 1) {
                InfiniteDurationResult.PositiveOverflow
            } else InfiniteDurationResult.NegativeOverflow
        }

        // 16. Return ? CreateTimeDurationRecord(days, hours × sign, minutes × sign, seconds × sign, milliseconds × sign, microseconds × sign, nanoseconds × sign).
        return InfiniteDurationResult.Balanced(createTimeDurationRecord(
            days,
            sign * hours,
            sign * minutes,
            sign * seconds,
            sign * milliseconds,
            sign * microseconds,
            sign.toBigInteger() * nanoseconds,
        ))
    }

    @JvmStatic
    @ECMAImpl("7.5.20")
    fun unbalanceDurationRelative(years_: Int, months_: Int, weeks_: Int, days_: Int, largestUnit: String, relativeTo_: JSValue): DateDurationRecord {
        var relativeTo = relativeTo_
        var years = years_
        var months = months_
        var weeks = weeks_
        var days = days_

        // 1. If largestUnit is "year", or years, months, weeks, and days are all 0, then
        if (largestUnit == "year" || (years == 0 && months == 0 && weeks == 0 && days == 0)) {
            // a. Return ! CreateDateDurationRecord(years, months, weeks, days).
            return createDateDurationRecord(years, months, weeks, days)
        }

        // 2. Let sign be ! DurationSign(years, months, weeks, days, 0, 0, 0, 0, 0, 0).
        val sign = durationSign(DurationRecord(years, months, weeks, days, 0, 0, 0, 0, 0, BigInteger.ZERO))

        // 3. Assert: sign ≠ 0.
        ecmaAssert(sign != 0)

        // 4. Let oneYear be ! CreateTemporalDuration(sign, 0, 0, 0, 0, 0, 0, 0, 0, 0).
        val oneYear = createTemporalDuration(DurationRecord(sign, 0, 0, 0, 0, 0, 0, 0, 0, BigInteger.ZERO))
        
        // 5. Let oneMonth be ! CreateTemporalDuration(0, sign, 0, 0, 0, 0, 0, 0, 0, 0).
        val oneMonth = createTemporalDuration(DurationRecord(0, sign, 0, 0, 0, 0, 0, 0, 0, BigInteger.ZERO))
        
        // 6. Let oneWeek be ! CreateTemporalDuration(0, 0, sign, 0, 0, 0, 0, 0, 0, 0).
        val oneWeek = createTemporalDuration(DurationRecord(0, 0, sign, 0, 0, 0, 0, 0, 0, BigInteger.ZERO))

        // 7. If relativeTo is not undefined, then
        val calendar = if (relativeTo != JSUndefined) {
            // a. Set relativeTo to ? ToTemporalDate(relativeTo).
            relativeTo = toTemporalDate(relativeTo)

            // b. Let calendar be relativeTo.[[Calendar]].
            relativeTo[Slot.Calendar]
        }
        // 8. Else,
        else {
            // a. Let calendar be undefined.
            null
        }

        // 9. If largestUnit is "month", then
        if (largestUnit == "month") {
            // a. If calendar is undefined, then
            if (calendar == null) {
                // i. Throw a RangeError exception.
                Errors.TODO("unbalanceDurationRelative 1").throwRangeError()
            }

            // b. Let dateAdd be ? GetMethod(calendar, "dateAdd").
            val dateAdd = AOs.getMethod(calendar, "dateAdd".toValue())

            // c. Let dateUntil be ? GetMethod(calendar, "dateUntil").
            val dateUntil = AOs.getMethod(calendar, "dateUntil".toValue())

            // d. Repeat, while years ≠ 0,
            while (years != 0) {
                // i. Let newRelativeTo be ? CalendarDateAdd(calendar, relativeTo, oneYear, undefined, dateAdd).
                val newRelativeTo = calendarDateAdd(calendar, relativeTo, oneYear, null, dateAdd)

                // ii. Let untilOptions be OrdinaryObjectCreate(null).
                val untilOptions = JSObject.create()

                // iii. Perform ! CreateDataPropertyOrThrow(untilOptions, "largestUnit", "month").
                AOs.createDataPropertyOrThrow(untilOptions, "largestUnit".key(), "month".toValue())

                // iv. Let untilResult be ? CalendarDateUntil(calendar, relativeTo, newRelativeTo, untilOptions, dateUntil).
                val untilResult = calendarDateUntil(calendar, relativeTo, newRelativeTo, untilOptions, dateUntil)

                // v. Let oneYearMonths be untilResult.[[Months]].
                val oneYearMonths = untilResult[Slot.Months]

                // vi. Set relativeTo to newRelativeTo.
                relativeTo = newRelativeTo

                // vii. Set years to years - sign.
                years -= sign

                // viii. Set months to months + oneYearMonths.
                months += oneYearMonths
            }
        }
        // 10. Else if largestUnit is "week", then
        else if (largestUnit == "week") {
            // a. If calendar is undefined, then
            if (calendar == null) {
                // i. Throw a RangeError exception.
                Errors.TODO("unbalanceDurationRelative 2").throwRangeError()
            }

            // b. Let dateAdd be ? GetMethod(calendar, "dateAdd").
            val dateAdd = AOs.getMethod(calendar, "dateAdd".toValue())

            // c. Repeat, while years ≠ 0,
            while (years != 0) {
                // i. Let moveResult be ? MoveRelativeDate(calendar, relativeTo, oneYear, dateAdd).
                val moveResult = moveRelativeDate(calendar, relativeTo as JSObject, oneYear, dateAdd)

                // ii. Set relativeTo to moveResult.[[RelativeTo]].
                relativeTo = moveResult.relativeTo

                // iii. Set days to days + moveResult.[[Days]].
                days += moveResult.days

                // iv. Set years to years - sign.
                years -= sign
            }

            // d. Repeat, while months ≠ 0,
            while (months != 0) {
                // i. Let moveResult be ? MoveRelativeDate(calendar, relativeTo, oneMonth, dateAdd).
                val moveResult = moveRelativeDate(calendar, relativeTo as JSObject, oneMonth, dateAdd)

                // ii. Set relativeTo to moveResult.[[RelativeTo]].
                relativeTo = moveResult.relativeTo

                // iii. Set days to days + moveResult.[[Days]].
                days += moveResult.days

                // iv. Set months to months - sign.
                months -= sign
            }
        }
        // 11. Else,
        else {
            // a. If any of years, months, and weeks are not zero, then
            if (years != 0 || months != 0 || weeks != 0) {
                // i. If calendar is undefined, then
                if (calendar == null) {
                    // 1. Throw a RangeError exception.
                    Errors.TODO("unbalanceDurationRelative 3").throwRangeError()
                }

                // ii. Let dateAdd be ? GetMethod(calendar, "dateAdd").
                val dateAdd = AOs.getMethod(calendar, "dateAdd".toValue())

                // iv. Repeat, while months ≠ 0,
                while (years != 0) {
                    // 1. Let moveResult be ? MoveRelativeDate(calendar, relativeTo, oneMonth, dateAdd).
                    val moveResult = moveRelativeDate(calendar, relativeTo as JSObject, oneYear, dateAdd)

                    // 2. Set relativeTo to moveResult.[[RelativeTo]].
                    relativeTo = moveResult.relativeTo

                    // 3. Set days to days +moveResult.[[Days]].
                    days += moveResult.days

                    // 4. Set months to months - sign.
                    years -= sign
                }

                // v. Repeat, while weeks ≠ 0,
                while (months != 0) {
                    // 1. Let moveResult be ? MoveRelativeDate(calendar, relativeTo, oneWeek, dateAdd).
                    val moveResult = moveRelativeDate(calendar, relativeTo as JSObject, oneWeek, dateAdd)
                    
                    // 2. Set relativeTo to moveResult.[[RelativeTo]].
                    relativeTo = moveResult.relativeTo
                    
                    // 3. Set days to days + moveResult.[[Days]].
                    days += moveResult.days
                    
                    // 4. Set weeks to weeks - sign.
                    months -= sign
                }
            }
        }

        // 12. Return ? CreateDateDurationRecord(years, months, weeks, days).
        return createDateDurationRecord(years, months, weeks, days)
    }

    @JvmStatic
    @ECMAImpl("7.5.21")
    fun balanceDurationRelative(years_: Int, months_: Int, weeks_: Int, days_: Int, largestUnit: String, relativeTo_: JSValue): DateDurationRecord {
        var relativeTo = relativeTo_
        var years = years_
        var months = months_
        var weeks = weeks_
        var days = days_

        // 1. If largestUnit is not one of "year", "month", or "week", or years, months, weeks, and days are all 0, then
        if (largestUnit !in setOf("year", "month", "week") || (years == 0 && months == 0 && weeks == 0 && days == 0)) {
            // a. Return ! CreateDateDurationRecord(years, months, weeks, days).
            return createDateDurationRecord(years, months, weeks, days)
        }

        // 2. If relativeTo is undefined, then
        if (relativeTo == JSUndefined) {
            // a. Throw a RangeError exception.
            Errors.TODO("balanceDurationRecord").throwRangeError()
        }

        // 3. Let sign be ! DurationSign(years, months, weeks, days, 0, 0, 0, 0, 0, 0).
        val sign = durationSign(DurationRecord(years, months, weeks, days, 0, 0, 0, 0, 0, BigInteger.ZERO))

        // 4. Assert: sign ≠ 0.
        ecmaAssert(sign != 0)

        // 5. Let oneYear be ! CreateTemporalDuration(sign, 0, 0, 0, 0, 0, 0, 0, 0, 0).
        val oneYear = createTemporalDuration(DurationRecord(sign, 0, 0, 0, 0, 0, 0, 0, 0, BigInteger.ZERO))
        
        
        // 6. Let oneMonth be ! CreateTemporalDuration(0, sign, 0, 0, 0, 0, 0, 0, 0, 0).
        val oneMonth = createTemporalDuration(DurationRecord(0, sign, 0, 0, 0, 0, 0, 0, 0, BigInteger.ZERO))
        
        // 7. Let oneWeek be ! CreateTemporalDuration(0, 0, sign, 0, 0, 0, 0, 0, 0, 0).
        val oneWeek = createTemporalDuration(DurationRecord(0, 0, sign, 0, 0, 0, 0, 0, 0, BigInteger.ZERO))

        // 8. Set relativeTo to ? ToTemporalDate(relativeTo).
        relativeTo = toTemporalDate(relativeTo)

        // 9. Let calendar be relativeTo.[[Calendar]].
        val calendar = relativeTo[Slot.Calendar]

        // 10. If largestUnit is "year", then
        if (largestUnit == "year") {
            // a. Let dateAdd be ? GetMethod(calendar, "dateAdd").
            val dateAdd = AOs.getMethod(calendar, "dateAdd".toValue())

            // b. Let moveResult be ? MoveRelativeDate(calendar, relativeTo, oneYear, dateAdd).
            var moveResult = moveRelativeDate(calendar, relativeTo, oneYear, dateAdd)

            // c. Let newRelativeTo be moveResult.[[RelativeTo]].
            var newRelativeTo = moveResult.relativeTo

            // d. Let oneYearDays be moveResult.[[Days]].
            var oneYearDays = moveResult.days

            // e. Repeat, while abs(days) ≥ abs(oneYearDays),
            while (abs(days) >= abs(oneYearDays)) {
                // i. Set days to days - oneYearDays.
                days -= oneYearDays

                // ii. Set years to years + sign.
                years += sign

                // iii. Set relativeTo to newRelativeTo.
                relativeTo = newRelativeTo

                // iv. Set moveResult to ? MoveRelativeDate(calendar, relativeTo, oneYear, dateAdd).
                moveResult = moveRelativeDate(calendar, relativeTo, oneYear, dateAdd)

                // v. Set newRelativeTo to moveResult.[[RelativeTo]].
                newRelativeTo = moveResult.relativeTo

                // vi. Set oneYearDays to moveResult.[[Days]].
                oneYearDays = moveResult.days
            }

            // f. Set moveResult to ? MoveRelativeDate(calendar, relativeTo, oneMonth, dateAdd).
            moveResult = moveRelativeDate(calendar, relativeTo as JSObject, oneMonth, dateAdd)

            // g. Set newRelativeTo to moveResult.[[RelativeTo]].
            newRelativeTo = moveResult.relativeTo

            // h. Let oneMonthDays be moveResult.[[Days]].
            var oneMonthDays = moveResult.days

            // i. Repeat, while abs(days) ≥ abs(oneMonthDays),
            while (abs(days) >= abs(oneMonthDays)) {
                // i. Set days to days - oneMonthDays.
                days -= oneMonthDays

                // ii. Set months to months + sign.
                months += sign

                // iii. Set relativeTo to newRelativeTo.
                relativeTo = newRelativeTo

                // iv. Set moveResult to ? MoveRelativeDate(calendar, relativeTo, oneMonth, dateAdd).
                moveResult = moveRelativeDate(calendar, relativeTo, oneMonth, dateAdd)

                // v. Set newRelativeTo to moveResult.[[RelativeTo]].
                newRelativeTo = moveResult.relativeTo

                // vi. Set oneMonthDays to moveResult.[[Days]].
                oneMonthDays = moveResult.days
            }

            // j. Set newRelativeTo to ? CalendarDateAdd(calendar, relativeTo, oneYear, undefined, dateAdd).
            newRelativeTo = calendarDateAdd(calendar, relativeTo, oneYear, null, dateAdd)

            // k. Let dateUntil be ? GetMethod(calendar, "dateUntil").
            val dateUntil = AOs.getMethod(calendar, "dateUntil".toValue())

            // l. Let untilOptions be OrdinaryObjectCreate(null).
            var untilOptions = JSObject.create()

            // m. Perform ! CreateDataPropertyOrThrow(untilOptions, "largestUnit", "month").
            AOs.createDataPropertyOrThrow(untilOptions, "largestUnit".key(), "month".toValue())

            // n. Let untilResult be ? CalendarDateUntil(calendar, relativeTo, newRelativeTo, untilOptions, dateUntil).
            var untilResult = calendarDateUntil(calendar, relativeTo, newRelativeTo, untilOptions, dateUntil)

            // o. Let oneYearMonths be untilResult.[[Months]].
            var oneYearMonths = untilResult[Slot.Months]

            // p. Repeat, while abs(months) ≥ abs(oneYearMonths),
            while (abs(months) >= abs(oneYearMonths)) {
                // i. Set months to months - oneYearMonths.
                months -= oneYearMonths

                // ii. Set years to years + sign.
                years += sign

                // iii. Set relativeTo to newRelativeTo.
                relativeTo = newRelativeTo

                // iv. Set newRelativeTo to ? CalendarDateAdd(calendar, relativeTo, oneYear, undefined, dateAdd).
                newRelativeTo = calendarDateAdd(calendar, relativeTo, oneYear, null, dateAdd)

                // v. Set untilOptions to OrdinaryObjectCreate(null).
                untilOptions = JSObject.create()

                // vi. Perform ! CreateDataPropertyOrThrow(untilOptions, "largestUnit", "month").
                AOs.createDataPropertyOrThrow(untilOptions, "largestUnit".key(), "month".toValue())

                // vii. Set untilResult to ? CalendarDateUntil(calendar, relativeTo, newRelativeTo, untilOptions, dateUntil).
                untilResult = calendarDateUntil(calendar, relativeTo, newRelativeTo, untilOptions, dateUntil)

                // viii. Set oneYearMonths to untilResult.[[Months]].
                oneYearMonths = untilResult[Slot.Months]
            }
        }
        // 11. Else if largestUnit is "month", then
        else if (largestUnit == "month") {
            // a. Let dateAdd be ? GetMethod(calendar, "dateAdd").
            val dateAdd = AOs.getMethod(calendar, "dateAdd".toValue())

            // b. Let moveResult be ? MoveRelativeDate(calendar, relativeTo, oneMonth, dateAdd).
            var moveResult = moveRelativeDate(calendar, relativeTo, oneMonth, dateAdd)

            // c. Let newRelativeTo be moveResult.[[RelativeTo]].
            var newRelativeTo = moveResult.relativeTo

            // d. Let oneMonthDays be moveResult.[[Days]].
            var oneMonthDays = moveResult.days

            // e. Repeat, while abs(days) ≥ abs(oneMonthDays),
            while (abs(days) >= abs(oneMonthDays)) {
                // i. Set days to days - oneMonthDays.
                days -= oneMonthDays

                // ii. Set months to months + sign.
                months += sign

                // iii. Set relativeTo to newRelativeTo.
                relativeTo = newRelativeTo

                // iv. Set moveResult to ? MoveRelativeDate(calendar, relativeTo, oneMonth, dateAdd).
                moveResult = moveRelativeDate(calendar, relativeTo, oneMonth, dateAdd)

                // v. Set newRelativeTo to moveResult.[[RelativeTo]].
                newRelativeTo = moveResult.relativeTo

                // vi. Set oneMonthDays to moveResult.[[Days]].
                oneMonthDays = moveResult.days
            }
        }
        // 12. Else,
        else {
            // a. Assert: largestUnit is "week".
            ecmaAssert(largestUnit == "week")

            // b. Let dateAdd be ? GetMethod(calendar, "dateAdd").
            val dateAdd = AOs.getMethod(calendar, "dateAdd".toValue())

            // c. Let moveResult be ? MoveRelativeDate(calendar, relativeTo, oneWeek, dateAdd).
            var moveResult = moveRelativeDate(calendar, relativeTo, oneWeek, dateAdd)

            // d. Let newRelativeTo be moveResult.[[RelativeTo]].
            var newRelativeTo = moveResult.relativeTo

            // e. Let oneWeekDays be moveResult.[[Days]].
            var oneWeekDays = moveResult.days

            // f. Repeat, while abs(days) ≥ abs(oneWeekDays),
            while (abs(days) >= abs(oneWeekDays)) {
                // i. Set days to days - oneWeekDays.
                days -= oneWeekDays

                // ii. Set weeks to weeks + sign.
                weeks += days

                // iii. Set relativeTo to newRelativeTo.
                relativeTo = newRelativeTo

                // iv. Set moveResult to ? MoveRelativeDate(calendar, relativeTo, oneWeek, dateAdd).
                moveResult = moveRelativeDate(calendar, relativeTo, oneWeek, dateAdd)

                // v. Set newRelativeTo to moveResult.[[RelativeTo]].
                newRelativeTo = moveResult.relativeTo

                // vi. Set oneWeekDays to moveResult.[[Days]].
                oneWeekDays = moveResult.days
            }
        }

        // 13. Return ! CreateDateDurationRecord(years, months, weeks, days).
        return createDateDurationRecord(years, months, weeks, days)
    }

    @JvmStatic
    @ECMAImpl("7.5.22")
    fun addDuration(
        year1: Int, month1: Int, week1: Int, day1: Int, hour1: Int, minute1: Int, second1: Int, millisecond1: Int, microsecond1: Int, nanosecond1: BigInteger,
        year2: Int, month2: Int, week2: Int, day2: Int, hour2: Int, minute2: Int, second2: Int, millisecond2: Int, microsecond2: Int, nanosecond2: BigInteger,
        relativeTo: JSValue,
    ): DurationRecord {
        // 1. Let largestUnit1 be ! DefaultTemporalLargestUnit(y1, mon1, w1, d1, h1, min1, s1, ms1, mus1).
        val largestUnit1 = defaultTemporalLargestUnit(year1, month1, week1, day1, hour1, minute1, second1, millisecond1, microsecond1)
        
        // 2. Let largestUnit2 be ! DefaultTemporalLargestUnit(y2, mon2, w2, d2, h2, min2, s2, ms2, mus2).
        val largestUnit2 = defaultTemporalLargestUnit(year2, month2, week2, day2, hour2, minute2, second2, millisecond2, microsecond2)
    
        // 3. Let largestUnit be ! LargerOfTwoTemporalUnits(largestUnit1, largestUnit2).
        val largestUnit = largerOfTwoTemporalUnits(largestUnit1, largestUnit2)

        // 4. If relativeTo is undefined, then
        if (relativeTo == JSUndefined) {
            // a. If largestUnit is one of "year", "month", or "week", then
            if (largestUnit in setOf("year", "month", "week")) {
                // i. Throw a RangeError exception.
                Errors.TODO("addDuration").throwRangeError()
            }

            // b. Let result be ? BalanceDuration(d1 + d2, h1 + h2, min1 + min2, s1 + s2, ms1 + ms2, mus1 + mus2, ns1 + ns2, largestUnit).
            val result = balanceDuration(day1 + day2, hour1 + hour2, minute1 + minute2, second1 + second2, millisecond1 + millisecond2, microsecond1 + microsecond2, nanosecond1 + nanosecond2, largestUnit)

            // c. Return ! CreateDurationRecord(0, 0, 0, result.[[Days]], result.[[Hours]], result.[[Minutes]], result.[[Seconds]], result.[[Milliseconds]], result.[[Microseconds]], result.[[Nanoseconds]]).
            return createDurationRecord(0, 0, 0, result.days, result.hours, result.minutes, result.seconds, result.milliseconds, result.microseconds, result.nanoseconds)
        }

        // Note: relativeTo is undefined or an object. It is not undefined, according to above
        expect(relativeTo is JSObject)

        // 5. If relativeTo has an [[InitializedTemporalDate]] internal slot, then
        if (Slot.InitializedTemporalDate in relativeTo) {
            // a. Let calendar be relativeTo.[[Calendar]].
            val calendar = relativeTo[Slot.Calendar]

            // b. Let dateDuration1 be ! CreateTemporalDuration(y1, mon1, w1, d1, 0, 0, 0, 0, 0, 0).
            val dateDuration1 = createTemporalDuration(DurationRecord(year1, month1, week1, day1, 0, 0, 0, 0, 0, BigInteger.ZERO))
            
            // c. Let dateDuration2 be ! CreateTemporalDuration(y2, mon2, w2, d2, 0, 0, 0, 0, 0, 0).
            val dateDuration2 = createTemporalDuration(DurationRecord(year2, month2, week2, day2, 0, 0, 0, 0, 0, BigInteger.ZERO))

            // d. Let dateAdd be ? GetMethod(calendar, "dateAdd").
            val dateAdd = AOs.getMethod(calendar, "dateAdd".toValue())

            // e. Let intermediate be ? CalendarDateAdd(calendar, relativeTo, dateDuration1, undefined, dateAdd).
            val intermediate = calendarDateAdd(calendar, relativeTo, dateDuration1, null, dateAdd)
            
            // f. Let end be ? CalendarDateAdd(calendar, intermediate, dateDuration2, undefined, dateAdd).
            val end = calendarDateAdd(calendar, intermediate, dateDuration2, null, dateAdd)

            // g. Let dateLargestUnit be ! LargerOfTwoTemporalUnits("day", largestUnit).
            val dateLargestUnit = largerOfTwoTemporalUnits("day", largestUnit)

            // h. Let differenceOptions be OrdinaryObjectCreate(null).
            val differenceOptions = JSObject.create()

            // i. Perform ! CreateDataPropertyOrThrow(differenceOptions, "largestUnit", dateLargestUnit).
            AOs.createDataPropertyOrThrow(differenceOptions, "largestUnit".key(), dateLargestUnit.toValue())

            // j. Let dateDifference be ? CalendarDateUntil(calendar, relativeTo, end, differenceOptions).
            val dateDifference = calendarDateUntil(calendar, relativeTo, end, differenceOptions)

            // k. Let result be ? BalanceDuration(dateDifference.[[Days]], h1 + h2, min1 + min2, s1 + s2, ms1 + ms2, mus1 + mus2, ns1 + ns2, largestUnit).
            val result = balanceDuration(dateDifference[Slot.Days], hour1 + hour2, minute1 + minute2, second1 + second2, millisecond1 + millisecond2, microsecond1 + microsecond2, nanosecond1 + nanosecond2, largestUnit)

            // l. Return ! CreateDurationRecord(dateDifference.[[Years]], dateDifference.[[Months]], dateDifference.[[Weeks]], result.[[Days]], result.[[Hours]], result.[[Minutes]], result.[[Seconds]], result.[[Milliseconds]], result.[[Microseconds]], result.[[Nanoseconds]]).
            return createDurationRecord(
                dateDifference[Slot.Years],
                dateDifference[Slot.Months],
                dateDifference[Slot.Week],
                result.days,
                result.hours,
                result.minutes,
                result.seconds,
                result.milliseconds,
                result.microseconds,
                result.nanoseconds,
            )
        }

        // 6. Assert: relativeTo has an [[InitializedTemporalZonedDateTime]] internal slot.
        ecmaAssert(Slot.InitializedTemporalZonedDateTime in relativeTo)

        // 7. Let timeZone be relativeTo.[[TimeZone]].
        val timeZone = relativeTo[Slot.TimeZone]

        // 8. Let calendar be relativeTo.[[Calendar]].
        val calendar = relativeTo[Slot.Calendar]

        // 9. Let intermediateNs be ? AddZonedDateTime(relativeTo.[[Nanoseconds]], timeZone, calendar, y1, mon1, w1, d1, h1, min1, s1, ms1, mus1, ns1).
        val intermediateNs = addZonedDateTime(relativeTo[Slot.Nanoseconds], timeZone, calendar, year1, month1, week1, day1, hour1, minute1, second1, millisecond1, microsecond1, nanosecond1)

        // 10. Let endNs be ? AddZonedDateTime(intermediateNs, timeZone, calendar, y2, mon2, w2, d2, h2, min2, s2, ms2, mus2, ns2).
        val endNs = addZonedDateTime(intermediateNs, timeZone, calendar, year2, month2, week2, day2, hour2, minute2, second2, millisecond1, microsecond2, nanosecond2)

        // 11. If largestUnit is not one of "year", "month", "week", or "day", then
        if (largestUnit in setOf("year", "month", "week", "day")) {
            // a. Let result be ! DifferenceInstant(relativeTo.[[Nanoseconds]], endNs, 1, "nanosecond", largestUnit, "halfExpand").
            val result = differenceInstant(relativeTo[Slot.Nanoseconds], endNs, 1, "nanosecond", largestUnit, "halfExpand")

            // b. Return ! CreateDurationRecord(0, 0, 0, 0, result.[[Hours]], result.[[Minutes]], result.[[Seconds]], result.[[Milliseconds]], result.[[Microseconds]], result.[[Nanoseconds]]).
            return createDurationRecord(0, 0, 0, 0, result.hours, result.minutes, result.seconds, result.milliseconds, result.microseconds, result.nanoseconds)
        }

        // 12. Return ? DifferenceZonedDateTime(relativeTo.[[Nanoseconds]], endNs, timeZone, calendar, largestUnit, OrdinaryObjectCreate(null)).
        return differenceZonedDateTime(relativeTo[Slot.Nanoseconds], endNs, timeZone, calendar, largestUnit, JSObject.create())
    }

    @JvmStatic
    @ECMAImpl("7.5.23")
    fun daysUntil(earlier: JSObject, later: JSObject): Int {
        // 1. Let epochDays1 be MakeDay(𝔽(earlier.[[ISOYear]]), 𝔽(earlier.[[ISOMonth]] - 1), 𝔽(earlier.[[ISODay]])).
        // 2. Assert: epochDays1 is finite.
        val epochDays1 = AOs.makeDay(earlier[Slot.ISOYear], earlier[Slot.ISOMonth] - 1, earlier[Slot.ISODay])
        
        // 3. Let epochDays2 be MakeDay(𝔽(later.[[ISOYear]]), 𝔽(later.[[ISOMonth]] - 1), 𝔽(later.[[ISODay]])).
        // 4. Assert: epochDays2 is finite.
        val epochDays2 = AOs.makeDay(later[Slot.ISOYear], later[Slot.ISOMonth] - 1, later[Slot.ISODay])

        // 5. Return ℝ(epochDays2) - ℝ(epochDays1).
        return (epochDays2 - epochDays1).let {
            expect(it <= Int.MAX_VALUE)
            it.toInt()
        }
    }

    @JvmStatic
    @ECMAImpl("7.5.24")
    fun moveRelativeDate(calendar: JSObject, relativeTo: JSObject, duration: JSObject, dateAdd: JSValue?): MoveRelativeDateRecord {
        // 1. Let newDate be ? CalendarDateAdd(calendar, relativeTo, duration, undefined, dateAdd).
        val newDate = calendarDateAdd(calendar, relativeTo, duration, null, dateAdd)

        // 2. Let days be DaysUntil(relativeTo, newDate).
        val days = daysUntil(relativeTo, newDate)

        // 3. Return the Record { [[RelativeTo]]: newDate, [[Days]]: days }.
        return MoveRelativeDateRecord(newDate, days)
    }

    data class MoveRelativeDateRecord(val relativeTo: JSObject, val days: Int)

    @JvmStatic
    @ECMAImpl("7.5.25")
    fun moveRelativeZonedDateTime(zonedDateTime: JSObject, years: Int, months: Int, weeks: Int, days: Int): JSObject {
        // 1. Let intermediateNs be ? AddZonedDateTime(zonedDateTime.[[Nanoseconds]], zonedDateTime.[[TimeZone]], zonedDateTime.[[Calendar]], years, months, weeks, days, 0, 0, 0, 0, 0, 0).
        val intermediateNs = addZonedDateTime(
            zonedDateTime[Slot.Nanoseconds],
            zonedDateTime[Slot.TimeZone],
            zonedDateTime[Slot.Calendar],
            years,
            months,
            weeks,
            days,
            0,
            0,
            0,
            0,
            0,
            BigInteger.ZERO,
        )

        // 2. Return ! CreateTemporalZonedDateTime(intermediateNs, zonedDateTime.[[TimeZone]], zonedDateTime.[[Calendar]]).
        return createTemporalZonedDateTime(intermediateNs, zonedDateTime[Slot.TimeZone], zonedDateTime[Slot.Calendar])
    }

    @JvmStatic
    @ECMAImpl("7.5.26")
    fun roundDuration(
        duration: DurationRecord,
        increment: Int,
        unit: String,
        roundingMode: String,
        relativeTo_: JSValue? = JSUndefined,
    ): RoundDurationRecord {
        TODO()
    }

    data class RoundDurationRecord(val duration: DurationRecord, val remainder: BigInteger)

    @JvmStatic
    @ECMAImpl("7.5.27")
    fun adjustRoundedDurationDays(duration: DurationRecord, increment: Int, unit: String, roundingMode: String, relativeTo: JSValue): DurationRecord {
        // 1. If Type(relativeTo) is not Object; or relativeTo does not have an [[InitializedTemporalZonedDateTime]] internal slot; or unit is one of "year", "month", "week", or "day"; or unit is "nanosecond" and increment is 1, then
        if (relativeTo !is JSObject || Slot.InitializedTemporalZonedDateTime in relativeTo || unit.let { it == "year" || it == "month" || it == "week" || it == "day" } || (unit == "nanosecond" && increment == 1)) {
            // a. Return ! CreateDurationRecord(years, months, weeks, days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds).
            return createDurationRecord(duration)
        }

        // 2. Let timeRemainderNs be ! TotalDurationNanoseconds(0, hours, minutes, seconds, milliseconds, microseconds, nanoseconds, 0).
        var timeRemainderNs = totalDurationNanoseconds(0, duration.hours, duration.minutes, duration.seconds, duration.milliseconds, duration.microseconds, duration.nanoseconds, BigInteger.ZERO)

        val direction = when {
            // 3. If timeRemainderNs = 0, let direction be 0.
            timeRemainderNs == BigInteger.ZERO -> 0

            // 4. Else if timeRemainderNs < 0, let direction be -1.
            timeRemainderNs < BigInteger.ZERO -> -1

            // 5. Else, let direction be 1.
            else -> 1
        }

        // 6. Let dayStart be ? AddZonedDateTime(relativeTo.[[Nanoseconds]], relativeTo.[[TimeZone]], relativeTo.[[Calendar]], years, months, weeks, days, 0, 0, 0, 0, 0, 0).
        val dayStart = addZonedDateTime(relativeTo[Slot.Nanoseconds], relativeTo[Slot.TimeZone], relativeTo[Slot.Calendar], duration.years, duration.months, duration.weeks, duration.days, 0, 0, 0, 0, 0, BigInteger.ZERO)
        
        // 7. Let dayEnd be ? AddZonedDateTime(dayStart, relativeTo.[[TimeZone]], relativeTo.[[Calendar]], 0, 0, 0, direction, 0, 0, 0, 0, 0, 0).
        val dayEnd = addZonedDateTime(dayStart, relativeTo[Slot.TimeZone], relativeTo[Slot.Calendar], 0, 0, 0, direction, 0, 0, 0, 0, 0, BigInteger.ZERO)

        // 8. Let dayLengthNs be ℝ(dayEnd - dayStart).
        val dayLengthNs = dayStart - dayEnd

        // 9. If (timeRemainderNs - dayLengthNs) × direction < 0, then
        if ((timeRemainderNs - dayLengthNs) * direction.toBigInteger() < BigInteger.ZERO) {
            // a. Return ! CreateDurationRecord(years, months, weeks, days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds).
            return createDurationRecord(duration)
        }

        // 10. Set timeRemainderNs to ! RoundTemporalInstant(ℤ(timeRemainderNs - dayLengthNs), increment, unit, roundingMode).
        timeRemainderNs = roundTemporalInstant(timeRemainderNs - dayLengthNs, increment, unit, roundingMode)

        // 11. Let adjustedDateDuration be ? AddDuration(years, months, weeks, days, 0, 0, 0, 0, 0, 0, 0, 0, 0, direction, 0, 0, 0, 0, 0, 0, relativeTo).
        val adjustedDateDuration = addDuration(
            duration.years, duration.months, duration.weeks, duration.days, 0, 0, 0, 0, 0, BigInteger.ZERO, 
            0, 0, 0, direction, 0, 0, 0, 0, 0, BigInteger.ZERO,
            relativeTo
        )

        // 12. Let adjustedTimeDuration be ? BalanceDuration(0, 0, 0, 0, 0, 0, timeRemainderNs, "hour").
        val adjustedTimeDuration = balanceDuration(0, 0, 0, 0, 0, 0, timeRemainderNs, "hour")

        // 13. Return ! CreateDurationRecord(adjustedDateDuration.[[Years]], adjustedDateDuration.[[Months]], adjustedDateDuration.[[Weeks]], adjustedDateDuration.[[Days]], adjustedTimeDuration.[[Hours]], adjustedTimeDuration.[[Minutes]], adjustedTimeDuration.[[Seconds]], adjustedTimeDuration.[[Milliseconds]], adjustedTimeDuration.[[Microseconds]], adjustedTimeDuration.[[Nanoseconds]]).
        return createDurationRecord(
            adjustedDateDuration.years,
            adjustedDateDuration.months,
            adjustedDateDuration.weeks,
            adjustedDateDuration.days,
            adjustedTimeDuration.hours,
            adjustedTimeDuration.minutes,
            adjustedTimeDuration.seconds,
            adjustedTimeDuration.milliseconds,
            adjustedTimeDuration.microseconds,
            adjustedTimeDuration.nanoseconds,
        )
    }

    @JvmStatic
    @ECMAImpl("7.5.28")
    fun temporalDurationToString(duration: DurationRecord, precision: Int? /* null == "auto" */): String {
        // 1. Let sign be ! DurationSign(years, months, weeks, days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds).
        val sign = durationSign(duration)

        // 2. Set microseconds to microseconds + RoundTowardsZero(nanoseconds / 1000).
        var microseconds = duration.microseconds + roundTowardsZero(duration.nanoseconds.toBigDecimal() / BigDecimal.valueOf(1000L)).toInt()

        // 3. Set nanoseconds to remainder(nanoseconds, 1000).
        val nanoseconds = (duration.nanoseconds % BigInteger.valueOf(1000L)).toInt()

        // 4. Set milliseconds to milliseconds + RoundTowardsZero(microseconds / 1000).
        var milliseconds = duration.milliseconds + microseconds / 1000

        // 5. Set microseconds to remainder(microseconds, 1000).
        microseconds %= 1000

        // 6. Set seconds to seconds + RoundTowardsZero(milliseconds / 1000).
        val seconds = duration.seconds + milliseconds / 1000

        // 7. Set milliseconds to remainder(milliseconds, 1000).
        milliseconds %= 1000

        // 8. Let datePart be "".
        val datePart = StringBuilder()

        // 9. If years is not 0, then
        if (duration.years != 0) {
            // a. Set datePart to the string concatenation of abs(years) formatted as a decimal number and the code unit 0x0059 (LATIN CAPITAL LETTER Y).
            datePart.append(abs(duration.years))
            datePart.append('Y')
        }

        // 10. If months is not 0, then
        if (duration.months != 0) {
            // a. Set datePart to the string concatenation of datePart, abs(months) formatted as a decimal number, and the code unit 0x004D (LATIN CAPITAL LETTER M).
            datePart.append(abs(duration.months))
            datePart.append('M')
        }

        // 11. If weeks is not 0, then
        if (duration.weeks != 0) {
            // a. Set datePart to the string concatenation of datePart, abs(weeks) formatted as a decimal number, and the code unit 0x0057 (LATIN CAPITAL LETTER W).
            datePart.append(abs(duration.weeks))
            datePart.append('W')
        }

        // 12. If days is not 0, then
        if (duration.days != 0) {
            // a. Set datePart to the string concatenation of datePart, abs(days) formatted as a decimal number, and the code unit 0x0044 (LATIN CAPITAL LETTER D).
            datePart.append(abs(duration.days))
            datePart.append('D')
        }

        // 13. Let timePart be "".
        val timePart = StringBuilder()

        // 14. If hours is not 0, then
        if (duration.hours != 0) {
            // a. Set timePart to the string concatenation of abs(hours) formatted as a decimal number and the code unit 0x0048 (LATIN CAPITAL LETTER H).
            timePart.append(abs(duration.hours))
            timePart.append('H')
        }

        // 15. If minutes is not 0, then
        if (duration.minutes != 0) {
            // a. Set timePart to the string concatenation of timePart, abs(minutes) formatted as a decimal number, and the code unit 0x004D (LATIN CAPITAL LETTER M).
            timePart.append(abs(duration.minutes))
            timePart.append('M')
        }

        // 16. If any of seconds, milliseconds, microseconds, and nanoseconds are not 0; or years, months, weeks, days, hours, and minutes are all 0; or precision is not "auto", then
        if (duration.run { 
            seconds != 0 || milliseconds != 0 || microseconds != 0 || nanoseconds != 0 || 
            (years == 0 && months == 0 && weeks == 0 && days == 0 && hours == 0) ||
            precision != null
        }) {
            // a. Let fraction be abs(milliseconds) × 10^6 + abs(microseconds) × 10^3 + abs(nanoseconds).
            val fraction = abs(milliseconds) * 1_000_000 + abs(microseconds) * 1_000 + nanoseconds

            // b. Let decimalPart be ToZeroPaddedDecimalString(fraction, 9).
            var decimalPart = fraction.toString().padStart(9, '0')

            // c. If precision is "auto", then
            decimalPart = if (precision == null) {
                // i. Set decimalPart to the longest possible substring of decimalPart starting at position 0 and not ending with the code unit 0x0030 (DIGIT ZERO).
                decimalPart.dropLastWhile { it == '0' }
            }
            else {
                // d. Else if precision = 0, then
                //    i. Set decimalPart to "".
                // e. Else,
                //    i. Set decimalPart to the substring of decimalPart from 0 to precision.
                decimalPart.substring(0, precision)
            }

            // f. Let secondsPart be abs(seconds) formatted as a decimal number.
            var secondsPart = abs(seconds).toString()

            // g. If decimalPart is not "", then
            if (decimalPart.isNotEmpty()) {
                // i. Set secondsPart to the string-concatenation of secondsPart, the code unit 0x002E (FULL STOP), and decimalPart.
                secondsPart = "$secondsPart.$decimalPart"
            }

            // h. Set timePart to the string concatenation of timePart, secondsPart, and the code unit 0x0053 (LATIN CAPITAL LETTER S).
            timePart.append(secondsPart)
            timePart.append('S')
        }

        // 17. Let signPart be the code unit 0x002D (HYPHEN-MINUS) if sign < 0, and otherwise the empty String.
        val signPart = if (sign < 0) "-" else ""

        // 18. Let result be the string concatenation of signPart, the code unit 0x0050 (LATIN CAPITAL LETTER P) and datePart.
        var result = signPart + "P" + datePart.toString()

        // 19. If timePart is not "", then
        if (timePart.isNotEmpty()) {
            // a. Set result to the string concatenation of result, the code unit 0x0054 (LATIN CAPITAL LETTER T), and timePart.
            result += 'T' + timePart.toString()
        }

        // 20. Return result.
        return result
    }

    @JvmStatic
    @ECMAImpl("7.5.29")
    fun addDurationToOrSubtractDurationFromDuration(isAdd: Boolean, duration: JSObject, other: JSValue, options: JSValue): JSObject {
        // 1. If operation is subtract, let sign be -1. Otherwise, let sign be 1.
        val sign = if (isAdd) 1 else -1

        // 2. Set other to ? ToTemporalDurationRecord(other).
        val other_ = toTemporalDurationRecord(other)

        // 3. Set options to ? GetOptionsObject(options).
        val options_ = getOptionsObject(options)

        // 4. Let relativeTo be ? ToRelativeTemporalObject(options).
        val relativeTo = toRelativeTemporalObject(options_)

        // 5. Let result be ? AddDuration(duration.[[Years]], duration.[[Months]], duration.[[Weeks]], duration.[[Days]], duration.[[Hours]], duration.[[Minutes]], duration.[[Seconds]], duration.[[Milliseconds]], duration.[[Microseconds]], duration.[[Nanoseconds]], sign × other.[[Years]], sign × other.[[Months]], sign × other.[[Weeks]], sign × other.[[Days]], sign × other.[[Hours]], sign × other.[[Minutes]], sign × other.[[Seconds]], sign × other.[[Milliseconds]], sign × other.[[Microseconds]], sign × other.[[Nanoseconds]], relativeTo).
        val result = addDuration(
            duration[Slot.Years],
            duration[Slot.Months],
            duration[Slot.Weeks],
            duration[Slot.Days],
            duration[Slot.Hours],
            duration[Slot.Minutes],
            duration[Slot.Seconds],
            duration[Slot.Milliseconds],
            duration[Slot.Microseconds],
            duration[Slot.Nanoseconds],
            sign * other_.years,
            sign * other_.months,
            sign * other_.weeks,
            sign * other_.days,
            sign * other_.hours,
            sign * other_.minutes,
            sign * other_.seconds,
            sign * other_.milliseconds,
            sign * other_.microseconds,
            sign.toBigInteger() * other_.nanoseconds,
            relativeTo,
        )

        // 6. Return ! CreateTemporalDuration(result.[[Years]], result.[[Months]], result.[[Weeks]], result.[[Days]], result.[[Hours]], result.[[Minutes]], result.[[Seconds]], result.[[Milliseconds]], result.[[Microseconds]], result.[[Nanoseconds]]).
        return createTemporalDuration(DurationRecord(
            result.years,
            result.months,
            result.weeks,
            result.days,
            result.hours,
            result.minutes,
            result.seconds,
            result.milliseconds,
            result.microseconds,
            result.nanoseconds,
        ))
    }

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
        val offsetString = result.tzOffset

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
        val result2 = utc - offsetNanoseconds

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
    fun differenceInstant(nanoseconds1: BigInteger, nanoseconds2: BigInteger, roundingIncrement: Int, smallestUnit: String, largestUnit: String, roundingMode: String): TimeDurationRecord {
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
    @ECMAImpl("8.5.8")
    fun roundTemporalInstant(ns: BigInteger, increment: Int, unit: String, roundingMode: String): BigInteger {
        // 1. Assert: Type(ns) is BigInt.

        val incrementNs = when (unit) {
            // 2. If unit is "hour", then
            //    a. Let incrementNs be increment × 3.6 × 10^12.
            "hour" -> 36.toBigInteger() * BigInteger.TEN.pow(11)

            // 3. Else if unit is "minute", then
            //    a. Let incrementNs be increment × 6 × 10^10.
            "minute" -> 6.toBigInteger() * BigInteger.TEN.pow(10)

            // 4. Else if unit is "second", then
            //    a. Let incrementNs be increment × 10^9.
            "second" -> BigInteger.TEN.pow(9)

            // 5. Else if unit is "millisecond", then
            //    a. Let incrementNs be increment × 10^6.
            "millisecond" -> BigInteger.TEN.pow(6)

            // 6. Else if unit is "microsecond", then
            //    a. Let incrementNs be increment × 10^3.
            "microsecond" -> BigInteger.TEN.pow(3)

            // 7. Else,
            //    a. Assert: unit is "nanosecond".
            //    b. Let incrementNs be increment.
            else -> {
                ecmaAssert(unit == "nanosecond")
                BigInteger.ONE
            }
        } * increment.toBigInteger()

        // 8. Return RoundNumberToIncrementAsIfPositive(ℝ(ns), incrementNs, roundingMode).
        return roundNumberToIncrementAsIfPositive(ns, incrementNs, roundingMode)
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
        return createTemporalDuration(DurationRecord(
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
        ))
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
    @ECMAImpl("11.1.1")
    fun isValidTimeZoneName(timeZone: String): Boolean {
        // TODO: Support Intl

        // 1. If timeZone is an ASCII-case-insensitive match for "UTC", return true
        // 2. return false
        return timeZone.lowercase() == "utc"
    }

    @JvmStatic
    @ECMAImpl("11.1.2")
    fun canonicalizeTimeZoneName(timeZone: String): String {
        // TODO: Support Intl

        // 1. Return "UTC"/
        return "UTC"
    }

    @JvmStatic
    @ECMAImpl("11.6.1")
    fun createTemporalTimeZone(identifier: String, newTarget: JSValue? = null): JSObject {
        TODO()
    }

    @JvmStatic
    @ECMAImpl("11.6.8")
    fun parseTimeZoneOffsetString(offsetString: String): BigInteger {
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
        return (factor * ((hoursMV * 60L + minutesMV) * 60L + secondsMV)).toBigInteger() * 
            BigInteger.TEN.pow(9) + nanosecondsMV.toBigInteger()
    }

    @JvmStatic
    @ECMAImpl("11.6.10")
    fun toTemporalTimeZone(temporalTimeZoneLike: JSValue): JSObject {
        TODO()
    }
    
    @JvmStatic
    @ECMAImpl("11.6.11")
    fun getOffsetNanosecondsFor(timeZone: JSObject, instant: JSObject): BigInteger {
        TODO()
    }

    @JvmStatic
    @ECMAImpl("11.6.14")
    fun builtinTimeZoneGetInstantFor(timeZone: JSObject, dateTime: JSObject, disambiguation: String): JSObject {
        TODO()
    }

    @JvmStatic
    @ECMAImpl("11.6.15")
    fun disambiguatePossibleInstants(possibleInstants: List<JSObject>, timeZone: JSObject, dateTime: JSObject, disambiguation: String): JSObject {
        TODO()
    }

    @JvmStatic
    @ECMAImpl("11.6.16")
    fun getPossibleInstantsFor(timeZone: JSObject, dateTime: JSObject): List<JSObject> {
        TODO()
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
    @ECMAImpl("12.2.3")
    fun getISO8601Calendar(): JSObject {
        TODO()
    }

    @JvmStatic
    @ECMAImpl("12.2.4")
    fun calendarFields(calendar: JSObject, fieldNames: List<String>): List<String> {
        TODO()
    }

    @JvmStatic
    @ECMAImpl("12.2.6")
    fun calendarDateAdd(calendar: JSObject, date: JSValue, duration: JSValue, options: JSObject? = null, dateAdd: JSValue? = null): JSObject {
        TODO()
    }

    @JvmStatic
    @ECMAImpl("12.2.7")
    fun calendarDateUntil(calendar: JSObject, one: JSValue, two: JSValue, options: JSObject, dateUntil: JSValue? = null): JSObject {
        TODO()
    }

    @JvmStatic
    @ECMAImpl("12.2.21")
    fun toTemporalCalendarWithISODefault(temporalCalendarLike: JSValue): JSObject {
        TODO()
    }

    @JvmStatic
    @ECMAImpl("12.2.22")
    fun getTemporalCalendarWithISODefault(item: JSObject): JSObject {
        TODO()
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
        )
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
    @ECMAImpl("13.12")
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
        )
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
    @ECMAImpl("13.14")
    fun toSecondsStringPrecision(normalizedOptions: JSObject): SecondsStringPrecisionRecord {
        // 1. Let smallestUnit be ? GetTemporalUnit(normalizedOptions, "smallestUnit", time, undefined).
        val smallestUnit = getTemporalUnit(normalizedOptions, "smallestUnit".key(), "time", TemporalUnitDefault.Value(JSUndefined))

        // 2. If smallestUnit is "hour", throw a RangeError exception.
        if (smallestUnit == "hour")
            Errors.TODO("toSecondsStringPrecision 1").throwRangeError()

        // 3. If smallestUnit is "minute", then
        if (smallestUnit == "minute") {
            // a. Return the Record { [[Precision]]: "minute", [[Unit]]: "minute", [[Increment]]: 1 }.
            return SecondsStringPrecisionRecord("minute", "minute", 1)
        }

        // 4. If smallestUnit is "second", then
        if (smallestUnit == "second") {
            // a. Return the Record { [[Precision]]: 0, [[Unit]]: "second", [[Increment]]: 1 }.
            return SecondsStringPrecisionRecord(0, "second", 1)
        }
        // 5. If smallestUnit is "millisecond", then
        if (smallestUnit == "millisecond") {
            // a. Return the Record { [[Precision]]: 3, [[Unit]]: "millisecond", [[Increment]]: 1 }.
            return SecondsStringPrecisionRecord(3, "millisecond", 1)
        }
        // 6. If smallestUnit is "microsecond", then
        if (smallestUnit == "microsecond") {
            // a. Return the Record { [[Precision]]: 6, [[Unit]]: "microsecond", [[Increment]]: 1 }.
            return SecondsStringPrecisionRecord(6, "microsecond", 1)
        }

        // 7. If smallestUnit is "nanosecond", then
        if (smallestUnit == "nanosecond") {
            // a. Return the Record { [[Precision]]: 9, [[Unit]]: "nanosecond", [[Increment]]: 1 }.
            return SecondsStringPrecisionRecord(9, "nanosecond", 1)
        }

        // 8. Assert: smallestUnit is undefined.
        ecmaAssert(smallestUnit == null)

        // 9. Let fractionalDigitsVal be ? Get(normalizedOptions, "fractionalSecondDigits").
        val fractionalDigitsVal = normalizedOptions.get("fractionalSecondDigits")

        // 10. If Type(fractionalDigitsVal) is not Number, then
        if (fractionalDigitsVal !is JSNumber) {
            // a. If fractionalDigitsVal is not undefined, then
            if (fractionalDigitsVal != JSUndefined) {
                // i. If ? ToString(fractionalDigitsVal) is not "auto", throw a RangeError exception.
                if (fractionalDigitsVal.toJSString().string != "auto")
                    Errors.TODO("toSecondsStringPrecision 2").throwRangeError()
            }

            // b. Return the Record { [[Precision]]: "auto", [[Unit]]: "nanosecond", [[Increment]]: 1 }.
            return SecondsStringPrecisionRecord("auto", "nanosecond", 1)
        }

        // 11. If fractionalDigitsVal is NaN, +∞𝔽, or -∞𝔽, throw a RangeError exception.
        if (fractionalDigitsVal.isNaN || !fractionalDigitsVal.isFinite)
            Errors.TODO("toSecondsStringPrecision 3").throwRangeError()

        // 12. Let fractionalDigitCount be RoundTowardsZero(ℝ(fractionalDigitsVal)).
        val fractionalDigitCount = fractionalDigitsVal.number.toInt()

        // 13. If fractionalDigitCount < 0 or fractionalDigitCount > 9, throw a RangeError exception.
        if (fractionalDigitCount !in 0..9)
            Errors.TODO("toSecondsStringPrecision 4").throwRangeError()

        // 14. If fractionalDigitCount is 0, then
        if (fractionalDigitCount == 0) {
            // a. Return the Record { [[Precision]]: 0, [[Unit]]: "second", [[Increment]]: 1 }.
            return SecondsStringPrecisionRecord(0, "second", 1)
        }

        // 15. If fractionalDigitCount is 1, 2, or 3, then
        if (fractionalDigitCount in 1..3) {
            // a. Return the Record { [[Precision]]: fractionalDigitCount, [[Unit]]: "millisecond", [[Increment]]: 10^3 - fractionalDigitCount }.
            return SecondsStringPrecisionRecord(fractionalDigitCount, "millisecond", 1_000 - fractionalDigitCount)
        }
        
        // 16. If fractionalDigitCount is 4, 5, or 6, then
        if (fractionalDigitCount in 4..6) {
            // a. Return the Record { [[Precision]]: fractionalDigitCount, [[Unit]]: "microsecond", [[Increment]]: 10^6 - fractionalDigitCount }.
            return SecondsStringPrecisionRecord(fractionalDigitCount, "microsecond", 1_000_000 - fractionalDigitCount)
        }

        // 17. Assert: fractionalDigitCount is 7, 8, or 9.
        ecmaAssert(fractionalDigitCount in 7..9)

        // 18. Return the Record { [[Precision]]: fractionalDigitCount, [[Unit]]: "nanosecond", [[Increment]]: 10^9 - fractionalDigitCount }.
        return SecondsStringPrecisionRecord(fractionalDigitCount, "nanosecond", 1_000_000_000 - fractionalDigitCount)
    }

    data class SecondsStringPrecisionRecord(val precision: Any /* String | Int */, val unit: String, val increment: Int)

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

    @JvmStatic
    @ECMAImpl("13.16")
    fun toRelativeTemporalObject(options: JSValue): JSValue {
        // 1. Assert: Type(options) is Object.
        ecmaAssert(options is JSObject)

        // 2. Let value be ? Get(options, "relativeTo").
        val value = options.get("relativeTo".key())

        // 3. If value is undefined, then
        if (value == JSUndefined) {
            // a. Return value.
            return value
        }

        // 4. Let offsetBehaviour be option.
        var offsetBehavior = "option"

        // 5. Let matchBehaviour be match exactly.
        var matchBehavior = "match exactly"

        var timeZone: JSObject?
        var offsetString: JSValue
        var result: DateTimeRecord?
        var calendar: JSObject?

        // 6. If Type(value) is Object, then
        if (value is JSObject) {
            // a. If value has either an [[InitializedTemporalDate]] or [[InitializedTemporalZonedDateTime]] internal slot, then
            if (Slot.InitializedTemporalDate in value || Slot.InitializedTemporalZonedDateTime in value) {
                // i. Return value.
                return value
            }

            // b. If value has an [[InitializedTemporalDateTime]] internal slot, then
            if (Slot.InitializedTemporalDateTime in value) {
                // i. Return ! CreateTemporalDate(value.[[ISOYear]], value.[[ISOMonth]], value.[[ISODay]], value.[[Calendar]]).
                return createTemporalDate(
                    value[Slot.ISOYear],
                    value[Slot.ISOMonth],
                    value[Slot.ISODay],
                    value[Slot.Calendar],
                )
            }

            // c. Let calendar be ? GetTemporalCalendarWithISODefault(value).
            calendar = getTemporalCalendarWithISODefault(value)

            // d. Let fieldNames be ? CalendarFields(calendar, « "day", "hour", "microsecond", "millisecond", "minute", "month", "monthCode", "nanosecond", "second", "year" »).
            val fieldNames = calendarFields(calendar, listOf("day", "hours", "microseconds", "millisecond", "minute", "month", "monthCode", "nanosecond", "second", "year"))

            // e. Let fields be ? PrepareTemporalFields(value, fieldNames, «»).
            val fields = prepareTemporalFields(value, fieldNames, emptySet())

            // f. Let dateOptions be OrdinaryObjectCreate(null).
            val dateOptions = JSObject.create()

            // g. Perform ! CreateDataPropertyOrThrow(dateOptions, "overflow", "constrain").
            AOs.createDataPropertyOrThrow(dateOptions, "overflow".key(), "constrain".toValue())

            // h. Let result be ? InterpretTemporalDateTimeFields(calendar, fields, dateOptions).
            result = interpretTemporalDateTimeFields(calendar, fields, dateOptions)

            // i. Let offsetString be ? Get(value, "offset").
            offsetString = value.get("offset")

            // j. Let timeZone be ? Get(value, "timeZone").
            timeZone = value.get("timeZone").let {
                if (it == JSUndefined) null else {
                    expect(it is JSObject)
                    it
                }
            }

            // k. If timeZone is not undefined, then
            if (timeZone != null) {
                // i. Set timeZone to ? ToTemporalTimeZone(timeZone).
                timeZone = toTemporalTimeZone(timeZone)
            }

            // l. If offsetString is undefined, then
            if (offsetString == JSUndefined) {
                // i. Set offsetBehaviour to wall.
                offsetBehavior = "wall"
            }
        }
        // 7. Else,
        else {
            // a. Let string be ? ToString(value).
            val string = value.toJSString().string

            // b. Let result be ? ParseTemporalRelativeToString(string).
            val result2 = parseTemporalRelativeToString(string)
            result = result2.toDateTime()

            // c. Let calendar be ? ToTemporalCalendarWithISODefault(result.[[Calendar]]).
            calendar = toTemporalCalendarWithISODefault(result2.calendar?.toValue() ?: JSUndefined)

            // d. Let offsetString be result.[[TimeZone]].[[OffsetString]].
            offsetString = result2.tzOffset?.toValue() ?: JSUndefined

            // e. Let timeZoneName be result.[[TimeZone]].[[Name]].
            var timeZoneName = result2.tzName

            // f. If timeZoneName is undefined, then
            if (timeZoneName == null) {
                // i. Let timeZone be undefined.
                timeZone = null
            }
            // g. Else,
            else {
                // i. If ParseText(StringToCodePoints(timeZoneName), TimeZoneNumericUTCOffset) is a List of errors, then

                // 1. If ! IsValidTimeZoneName(timeZoneName) is false, throw a RangeError exception.
                if (!isValidTimeZoneName(timeZoneName))
                    Errors.TODO("toRelativeTemporalObject").throwRangeError()

                // 2. Set timeZoneName to ! CanonicalizeTimeZoneName(timeZoneName).
                timeZoneName = canonicalizeTimeZoneName(timeZoneName)

                // ii. Let timeZone be ! CreateTemporalTimeZone(timeZoneName).
                timeZone = createTemporalTimeZone(timeZoneName)

                // iii. If result.[[TimeZone]].[[Z]] is true, then
                if (result2.z) {
                    // 1. Set offsetBehaviour to exact.
                    offsetBehavior = "exact"
                }
                // iv. Else if offsetString is undefined, then
                else if (offsetString == JSUndefined) {
                    // 1. Set offsetBehaviour to wall.
                    offsetBehavior = "wall"
                }

                // v. Set matchBehaviour to match minutes.
                matchBehavior = "match minutes"
            }
        }

        // 8. If timeZone is undefined, then
        if (timeZone == null) {
            // a. Return ? CreateTemporalDate(result.[[Year]], result.[[Month]], result.[[Day]], calendar).
            return createTemporalDate(result.year, result.month, result.day, calendar)
        }

        // 9. If offsetBehaviour is option, then
        val offsetNs = if (offsetBehavior == "option") {
            // a. Set offsetString to ? ToString(offsetString).
            // b. Let offsetNs be ? ParseTimeZoneOffsetString(offsetString).
            parseTimeZoneOffsetString(offsetString.toJSString().string)
        }
        // 10. Else,
        else {
            // a. Let offsetNs be 0.
            BigInteger.ZERO
        }

        // 11. Let epochNanoseconds be ? InterpretISODateTimeOffset(result.[[Year]], result.[[Month]], result.[[Day]], result.[[Hour]], result.[[Minute]], result.[[Second]], result.[[Millisecond]], result.[[Microsecond]], result.[[Nanosecond]], offsetBehaviour, offsetNs, timeZone, "compatible", "reject", matchBehaviour).
        val epochNanoseconds = interpretISODateTimeOffset(
            result.year,
            result.month,
            result.day,
            result.hour,
            result.minute,
            result.second,
            result.millisecond,
            result.microsecond,
            result.nanosecond,
            offsetBehavior, 
            offsetNs,
            timeZone,
            "compatible",
            "reject",
            matchBehavior,
        )

        // 12. Return ! CreateTemporalZonedDateTime(epochNanoseconds, timeZone, calendar).
        return createTemporalZonedDateTime(epochNanoseconds, timeZone, calendar)
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
    @ECMAImpl("13.23")
    fun getUnsignedRoundingMode(roundingMode: String, isNegative: Boolean): RoundingMode {
        // 1. If isNegative is true, return the specification type in the third column of Table 14 where 
        //    the first column is roundingMode and the second column is "negative".
        // 2. Else, return the specification type in the third column of Table 14 where the first column
        //    is roundingMode and the second column is "positive".

        return unsignedRoundingModes[roundingMode]!!.let {
            if (isNegative) it.second else it.first
        }
    }

    // String -> Pair<RoundingMode (positive), RoundingMode (negateive)>
    // infinity -> CEILING
    // zero -> FLOOR
    // half-infinity -> HALF_UP
    // half-zero -> HALF_DOWN
    // half-even -> HALF_EVEN
    private val unsignedRoundingModes = mapOf(
        "ceil" to (RoundingMode.CEILING to RoundingMode.FLOOR),
        "floor" to (RoundingMode.FLOOR to RoundingMode.CEILING),
        "expand" to (RoundingMode.CEILING to RoundingMode.CEILING),
        "trunc" to (RoundingMode.FLOOR to RoundingMode.FLOOR),
        "halfCeil" to (RoundingMode.HALF_UP to RoundingMode.HALF_DOWN),
        "halfFloor" to (RoundingMode.HALF_DOWN to RoundingMode.HALF_UP),
        "halfExpand" to (RoundingMode.HALF_UP to RoundingMode.HALF_UP),
        "halfTrunc" to (RoundingMode.HALF_DOWN to RoundingMode.HALF_DOWN),
        "halfEven" to (RoundingMode.HALF_EVEN to RoundingMode.HALF_EVEN),
    )

    @JvmStatic
    @ECMAImpl("13.26")
    fun roundNumberToIncrementAsIfPositive(x: BigInteger, increment: BigInteger, roundingMode: String): BigInteger {
        // 1. Let quotient be x / increment.
        val quotient = x.toBigDecimal() / increment.toBigDecimal() 

        // 2. Let unsignedRoundingMode be GetUnsignedRoundingMode(roundingMode, false).
        val unsignedRoundingMode = getUnsignedRoundingMode(roundingMode, false)

        // 3. Let r1 be the largest integer such that r1 ≤ quotient.
        // 4. Let r2 be the smallest integer such that r2 > quotient.
        // 5. Let rounded be ApplyUnsignedRoundingMode(quotient, r1, r2, unsignedRoundingMode).
        var rounded = quotient.round(MathContext(0, unsignedRoundingMode)).toBigInteger()

        // 6. Return rounded × increment.
        return rounded * increment
    }

    @JvmStatic
    @ECMAImpl("13.32")
    fun roundNumberToIncrement(x: BigInteger, increment: BigInteger, roundingMode: String): BigInteger {
        // 1. Let quotient be x / increment.
        var quotient = x.toBigDecimal() / increment.toBigDecimal()
        
        // 2. If quotient < 0, then
        val isNegative = if (quotient < BigDecimal.ZERO) {
            // a. Let isNegative be true.
            // b. Set quotient to -quotient.
            quotient = -quotient
            true
        }
        // 3. Else,
        else {
            // a. Let isNegative be false.
            false
        }

        // 4. Let unsignedRoundingMode be GetUnsignedRoundingMode(roundingMode, isNegative).
        val unsignedRoundingMode = getUnsignedRoundingMode(roundingMode, isNegative)

        // 5. Let r1 be the largest integer such that r1 ≤ quotient.
        // 6. Let r2 be the smallest integer such that r2 > quotient.
        // 7. Let rounded be ApplyUnsignedRoundingMode(quotient, r1, r2, unsignedRoundingMode).
        var rounded = quotient.round(MathContext(0, unsignedRoundingMode)).toBigInteger()

        // 8. If isNegative is true, set rounded to -rounded.
        if (isNegative)
            rounded = -rounded

        // 9. Return rounded × increment.
        return rounded * increment
    }

    @JvmStatic
    @ECMAImpl("13.28")
    fun parseISODateTime(isoString: String): ParsedISODateTime {
        // Note: This implementation is largely derived from the polyfill

        // 1. Let parseResult be empty.
        // 2. For each nonterminal goal of « TemporalDateTimeString, TemporalInstantString, TemporalMonthDayString,
        //    TemporalTimeString, TemporalYearMonthString, TemporalZonedDateTimeString », do
        //    a. If parseResult is not a Parse Node, set parseResult to ParseText(StringToCodePoints(isoString), goal).
        // 3. If parseResult is not a Parse Node, throw a RangeError exception.
        val match = Regex.zonedDateTime.find(isoString) ?: Errors.Temporal.InvalidISO8601String(isoString).throwRangeError(realm)

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
        return ParsedISODateTime(
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
    fun parseTemporalInstantString(isoString: String): ParsedISODateTime {
        return parseISODateTime(isoString)
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
    @ECMAImpl("13.36")
    fun parseTemporalRelativeToString(isoString: String): ParsedISODateTime {
        // 1. If ParseText(StringToCodePoints(isoString), TemporalDateTimeString) is a List of errors, throw a RangeError exception.
        // 2. Return ? ParseISODateTime(isoString).
        return parseISODateTime(isoString)
    }
    @JvmStatic
    @ECMAImpl("13.40")
    fun toPositiveInteger(argument: JSValue): JSNumber {
        // 1. Let integer be ? ToIntegerThrowOnInfinity(argument).
        val integer = toIntegerThrowOnInfinity(argument)

        // 2. If integer ≤ 0, then
        if (integer.number <= 0) {
            // a. Throw a RangeError exception.
            Errors.TODO("toPositiveInteger").throwRangeError()
        }

        // 3. Return integer.
        return integer
    }

    @JvmStatic
    @ECMAImpl("13.41")
    fun toIntegerThrowOnInfinity(argument: JSValue): JSNumber {
        // 1. Let integer be ? ToIntegerOrInfinity(argument).
        val integer = argument.toIntegerOrInfinity()

        // 2. If integer is -∞ or +∞, then
        if (integer.isInfinite) {
            // a. Throw a RangeError exception.
            Errors.TODO("toIntegerThrowOnInfinity").throwRangeError()
        }

        // 3. Return integer.
        return integer
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
    @ECMAImpl("13.43")
    fun prepareTemporalFields(fields: JSObject, fieldNames: List<String>, requiredFields: Set<String>?): JSObject {
        // 1. Let result be OrdinaryObjectCreate(null).
        val result = JSObject.create()

        // 2. Let any be false.
        var any = false

        // 3. For each property name property of fieldNames, do
        for (property in fieldNames) {
            // a. Let value be ? Get(fields, property).
            val value = fields.get(property)

            // b. If value is not undefined, then
            if (value != JSUndefined) {
                // i. Set any to true.
                any = true

                // ii. If property is in the Property column of Table 15 and there is a Conversion value in the same row, then
                val requirements = temporalFieldRequirements[property]
                val value_ = if (requirements != null) {
                    // 1. Let Conversion be the Conversion value of the same row.
                    val conversion = requirements.first

                    // 2. If Conversion is ToIntegerThrowOnInfinity, then
                    if (conversion == "ToIntegerThrowOnInfinity") {
                        // a. Set value to ? ToIntegerThrowOnInfinity(value).
                        // b. Set value to 𝔽(value).
                        toIntegerThrowOnInfinity(value)
                    }
                    // 3. Else if Conversion is ToPositiveInteger, then
                    else if (conversion == "ToPositiveInteger") {
                        // a. Set value to ? ToPositiveInteger(value).
                        // b. Set value to 𝔽(value).
                        toPositiveInteger(value)
                    }
                    // 4. Else,
                    else {
                        // a. Assert: Conversion is ToString.
                        ecmaAssert(conversion == "ToString")

                        // b. Set value to ? ToString(value).
                        value.toJSString()
                    }
                } else value

                // iii. Perform ! CreateDataPropertyOrThrow(result, property, value).
                AOs.createDataPropertyOrThrow(result, property.key(), value_)
            }
            // c. Else if requiredFields is a List, then
            else if (requiredFields != null) {
                // i. If requiredFields contains property, then
                if (property in requiredFields) {
                    // 1. Throw a TypeError exception.
                    Errors.TODO("prepareTemporalFields 1").throwTypeError()
                }

                // ii. If property is in the Property column of Table 15, then
                val value_ = if (property in temporalFieldRequirements) {
                    // 1. Set value to the corresponding Default value of the same row.
                    temporalFieldRequirements[property]!!.second
                } else value

                // iii. Perform ! CreateDataPropertyOrThrow(result, property, value).
                AOs.createDataPropertyOrThrow(result, property.key(), value_)
            }
        }

        // 4. If requiredFields is partial and any is false, then
        if (requiredFields == null && !any) {
            // a. Throw a TypeError exception.
            Errors.TODO("prepareTemporalFields 2").throwTypeError()
        }

        // 5. Return result.
        return result
    }

    private val temporalFieldRequirements = mapOf(
        "year" to ("ToIntegerThrowOnInfinity" to JSUndefined),
        "months" to ("ToPositiveInteger" to JSUndefined),
        "monthCode" to ("ToString" to JSUndefined),
        "day" to ("ToPositiveInteger" to JSUndefined),
        "hour" to ("ToIntegerThrowOnInfinity" to JSNumber.ZERO),
        "minute" to ("ToIntegerThrowOnInfinity" to JSNumber.ZERO),
        "second" to ("ToIntegerThrowOnInfinity" to JSNumber.ZERO),
        "millisecond" to ("ToIntegerThrowOnInfinity" to JSNumber.ZERO),
        "microsecond" to ("ToIntegerThrowOnInfinity" to JSNumber.ZERO),
        "nanosecond" to ("ToIntegerThrowOnInfinity" to JSNumber.ZERO),
        "offset" to ("ToString" to JSUndefined),
        "era" to ("ToString" to JSUndefined),
        "eraYear" to ("ToIntegerThrowOnInfinity" to JSUndefined),
        "timeZone" to (null to JSUndefined),
    )

    @JvmStatic
    @ECMAImpl("13.44")
    fun getDifferenceSettings(
        isUntil: Boolean,
        options_: JSValue,
        unitGroup: String,
        disallowedUnits: List<String>,
        fallbackSmallestUnit: String,
        smallestLargestDefaultUnit: String,
    ): DifferenceSettingsRecord {
        // 1. Set options to ? GetOptionsObject(options).
        var options = getOptionsObject(options_)

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
        var nanoseconds: BigInteger, // TODO: This should be an Int according to the spec
    )

    data class DateDurationRecord(
        val years: Int,
        val months: Int,
        val weeks: Int, 
        val days: Int,
    )

    data class TimeDurationRecord(
        var days: Int,
        var hours: Int,
        var minutes: Int,
        var seconds: Int,
        var milliseconds: Int,
        var microseconds: Int,
        var nanoseconds: BigInteger,
    )

    data class DateTimeRecord(
        val year: Int,
        val month: Int,
        val day: Int,
        val hour: Int,
        val minute: Int,
        val second: Int,
        val millisecond: Int,
        val microsecond: Int,
        val nanosecond: BigInteger,
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

    data class ParsedISODateTime(
        val year: Int,
        val month: Int,
        val day: Int,
        val hour: Int,
        val minute: Int,
        val second: Int,
        val millisecond: Int,
        val microsecond: Int,
        val nanosecond: BigInteger,
        val tzName: String?,
        val tzOffset: String?,
        val z: Boolean,
        val calendar: String?,
    ) {
        fun toDateTime() = DateTimeRecord(year, month, day, hour, minute, second, millisecond, microsecond, nanosecond)
    }

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
