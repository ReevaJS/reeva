package com.reevajs.reeva.runtime.temporal

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.JSFalse
import com.reevajs.reeva.runtime.primitives.JSString
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.temporal.TemporalAOs
import com.reevajs.reeva.utils.*
import java.math.BigInteger

class JSZonedDateTimeProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()
        
        defineOwnProperty("constructor", realm.zonedDateTimeCtor)
        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "Temporal.ZonedDateTime".toValue(), attrs { +conf })

        defineBuiltinGetter("calendar", ::getCalendar)
        defineBuiltinGetter("timeZone", ::getTimeZone)
        defineBuiltinGetter("year", ::getYear)
        defineBuiltinGetter("month", ::getMonth)
        defineBuiltinGetter("monthCode", ::getMonthCode)
        defineBuiltinGetter("day", ::getDay)
        defineBuiltinGetter("hour", ::getHour)
        defineBuiltinGetter("minute", ::getMinute)
        defineBuiltinGetter("second", ::getSecond)
        defineBuiltinGetter("millisecond", ::getMillisecond)
        defineBuiltinGetter("microsecond", ::getMicrosecond)
        defineBuiltinGetter("nanosecond", ::getNanosecond)
        defineBuiltinGetter("epochSeconds", ::getEpochSeconds)
        defineBuiltinGetter("epochMilliseconds", ::getEpochMilliseconds)
        defineBuiltinGetter("epochMicroseconds", ::getEpochMicroseconds)
        defineBuiltinGetter("epochNanoseconds", ::getEpochNanoseconds)
        defineBuiltinGetter("dayOfWeek", ::getDayOfWeek)
        defineBuiltinGetter("dayOfYear", ::getDayOfYear)
        defineBuiltinGetter("weekOfYear", ::getWeekOfYear)
        defineBuiltinGetter("hoursInDay", ::getHoursInDay)
        defineBuiltinGetter("daysInWeek", ::getDaysInWeek)
        defineBuiltinGetter("daysInMonth", ::getDaysInMonth)
        defineBuiltinGetter("daysInYear", ::getDaysInYear)
        defineBuiltinGetter("monthsInYear", ::getMonthsInYear)
        defineBuiltinGetter("inLeapYear", ::getInLeapYear)
        defineBuiltinGetter("offsetNanoseconds", ::getOffsetNanoseconds)
        defineBuiltinGetter("offset", ::getOffset)

        defineBuiltin("with", 1, ::with)
        defineBuiltin("withPlainTime", 0, ::withPlainTime)
        defineBuiltin("withPlainDate", 1, ::withPlainDate)
        defineBuiltin("withTimeZone", 1, ::withTimeZone)
        defineBuiltin("withCalendar", 1, ::withCalendar)
        defineBuiltin("add", 1, ::add)
        defineBuiltin("subtract", 1, ::subtract)
        defineBuiltin("until", 1, ::until)
        defineBuiltin("since", 1, ::since)
        defineBuiltin("round", 1, ::round)
        defineBuiltin("equals", 1, ::equals)
        defineBuiltin("toString", 0, ::toString)
        defineBuiltin("toLocaleString", 0, ::toLocaleString)
        defineBuiltin("toJSON", 0, ::toJSON)
        defineBuiltin("valueOf", 0, ::valueOf)
        defineBuiltin("startOfDay", 0, ::startOfDay)
        defineBuiltin("toInstant", 0, ::toInstant)
        defineBuiltin("toPlainDate", 0, ::toPlainDate)
        defineBuiltin("toPlainTime", 0, ::toPlainTime)
        defineBuiltin("toPlainDateTime", 0, ::toPlainDateTime)
        defineBuiltin("toPlainYearMonth", 0, ::toPlainYearMonth)
        defineBuiltin("toPlainMonthDay", 0, ::toPlainMonthDay)
        defineBuiltin("getISOFields", 0, ::getISOFields)
    }

    companion object {
        @JvmStatic
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSZonedDateTimeProto(realm).initialize()

        private fun thisZonedDateTime(thisValue: JSValue, method: String): JSObject {
            if (!AOs.requireInternalSlot(thisValue, Slot.InitializedTemporalZonedDateTime))
                Errors.IncompatibleMethodCall("ZonedDateTime.prototype.$method").throwTypeError()
            return thisValue
        }

        @JvmStatic
        @ECMAImpl("6.3.3")
        fun getCalendar(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            // 3. Return zonedDateTime.[[Calendar]].
            return thisZonedDateTime(arguments.thisValue, "get calendar")[Slot.Calendar].toValue()
        }

        @JvmStatic
        @ECMAImpl("6.3.4")
        fun getTimeZone(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            // 3. Return zonedDateTime.[[TimeZone]].
            return thisZonedDateTime(arguments.thisValue, "get timeZone")[Slot.TimeZone]
        }

        @JvmStatic
        @ECMAImpl("6.3.5")
        fun getYear(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "get year")

            // 3. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.TimeZone]

            // 4. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanosecond])

            // 5. Let calendar be zonedDateTime.[[Calendar]].
            val calendar = zonedDateTime[Slot.Calendar]

            // 6. Let temporalDateTime be ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar).
            val temporalDateTime = TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar)

            // 7. Return ? CalendarYear(calendar, temporalDateTime).
            return TemporalAOs.calendarYear(calendar, temporalDateTime)
        }

        @JvmStatic
        @ECMAImpl("6.3.6")
        fun getMonth(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "get month")

            // 3. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.TimeZone]

            // 4. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanosecond])

            // 5. Let calendar be zonedDateTime.[[Calendar]].
            val calendar = zonedDateTime[Slot.Calendar]

            // 6. Let temporalDateTime be ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar).
            val temporalDateTime = TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar)

            // 7. Return ? CalendarMonth(calendar, temporalDateTime).
            return TemporalAOs.calendarMonth(calendar, temporalDateTime)
        }

        @JvmStatic
        @ECMAImpl("6.3.7")
        fun getMonthCode(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "get monthCode")

            // 3. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.TimeZone]

            // 4. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanosecond])

            // 5. Let calendar be zonedDateTime.[[Calendar]].
            val calendar = zonedDateTime[Slot.Calendar]

            // 6. Let temporalDateTime be ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar).
            val temporalDateTime = TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar)

            // 7. Return ? CalendarMonthCode(calendar, temporalDateTime).
            return TemporalAOs.calendarMonthCode(calendar, temporalDateTime)
        }

        @JvmStatic
        @ECMAImpl("6.3.8")
        fun getDay(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "get day")

            // 3. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.TimeZone]

            // 4. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanosecond])

            // 5. Let calendar be zonedDateTime.[[Calendar]].
            val calendar = zonedDateTime[Slot.Calendar]

            // 6. Let temporalDateTime be ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar).
            val temporalDateTime = TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar)

            // 7. Return ? CalendarDay(calendar, temporalDateTime).
            return TemporalAOs.calendarDay(calendar, temporalDateTime)
        }

        @JvmStatic
        @ECMAImpl("6.3.9")
        fun getHour(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "get hour")

            // 3. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.TimeZone]

            // 4. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanosecond])

            // 5. Let calendar be zonedDateTime.[[Calendar]].
            val calendar = zonedDateTime[Slot.Calendar]

            // 6. Let temporalDateTime be ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar).
            val temporalDateTime = TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar)

            // 7. Return 𝔽(temporalDateTime.[[ISOHour]]).
            return temporalDateTime[Slot.ISOHour].toValue()
        }

        @JvmStatic
        @ECMAImpl("6.3.10")
        fun getMinute(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "get minute")

            // 3. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.TimeZone]

            // 4. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanosecond])

            // 5. Let calendar be zonedDateTime.[[Calendar]].
            val calendar = zonedDateTime[Slot.Calendar]

            // 6. Let temporalDateTime be ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar).
            val temporalDateTime = TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar)

            // 7. Return 𝔽(temporalDateTime.[[ISOMinute]]).
            return temporalDateTime[Slot.ISOMinute].toValue()
        }

        @JvmStatic
        @ECMAImpl("6.3.11")
        fun getSecond(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "get second")

            // 3. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.TimeZone]

            // 4. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanosecond])

            // 5. Let calendar be zonedDateTime.[[Calendar]].
            val calendar = zonedDateTime[Slot.Calendar]

            // 6. Let temporalDateTime be ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar).
            val temporalDateTime = TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar)

            // 7. Return 𝔽(temporalDateTime.[[ISOSecond]]).
            return temporalDateTime[Slot.ISOSecond].toValue()
        }

        @JvmStatic
        @ECMAImpl("6.3.12")
        fun getMillisecond(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "get millisecond")

            // 3. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.TimeZone]

            // 4. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanosecond])

            // 5. Let calendar be zonedDateTime.[[Calendar]].
            val calendar = zonedDateTime[Slot.Calendar]

            // 6. Let temporalDateTime be ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar).
            val temporalDateTime = TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar)

            // 7. Return 𝔽(temporalDateTime.[[ISOMillisecond]]).
            return temporalDateTime[Slot.ISOMillisecond].toValue()
        }

        @JvmStatic
        @ECMAImpl("6.3.13")
        fun getMicrosecond(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "get microsecond")

            // 3. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.TimeZone]

            // 4. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanosecond])

            // 5. Let calendar be zonedDateTime.[[Calendar]].
            val calendar = zonedDateTime[Slot.Calendar]

            // 6. Let temporalDateTime be ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar).
            val temporalDateTime = TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar)

            // 7. Return 𝔽(temporalDateTime.[[ISOMicrosecond]]).
            return temporalDateTime[Slot.ISOMicrosecond].toValue()
        }

        @JvmStatic
        @ECMAImpl("6.3.14")
        fun getNanosecond(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "get nanosecond")

            // 3. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.TimeZone]

            // 4. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanosecond])

            // 5. Let calendar be zonedDateTime.[[Calendar]].
            val calendar = zonedDateTime[Slot.Calendar]

            // 6. Let temporalDateTime be ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar).
            val temporalDateTime = TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar)

            // 7. Return 𝔽(temporalDateTime.[[ISONanosecond]]).
            return temporalDateTime[Slot.ISONanosecond].toValue()
        }

        @JvmStatic
        @ECMAImpl("6.3.15")
        fun getEpochSeconds(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "get epochSeconds")

            // 3. Let ns be zonedDateTime.[[Nanoseconds]].
            val ns = zonedDateTime[Slot.Nanoseconds]

            // 4. Let s be RoundTowardsZero(ℝ(ns) / 10^9).
            val s = ns / BigInteger.valueOf(1_000_000_000L)

            // 5. Return 𝔽(s).
            return s.toValue()
        }

        @JvmStatic
        @ECMAImpl("6.3.16")
        fun getEpochMilliseconds(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "get epochMillieconds")

            // 3. Let ns be zonedDateTime.[[Nanoseconds]].
            val ns = zonedDateTime[Slot.Nanoseconds]

            // 4. Let ms be RoundTowardsZero(ℝ(ns) / 10^6).
            val ms = ns / BigInteger.valueOf(1_000_000L)

            // 5. Return 𝔽(s).
            return ms.toValue()
        }

        @JvmStatic
        @ECMAImpl("6.3.17")
        fun getEpochMicroseconds(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "get epochMicroseconds")

            // 3. Let ns be zonedDateTime.[[Nanoseconds]].
            val ns = zonedDateTime[Slot.Nanoseconds]

            // 4. Let µs be RoundTowardsZero(ℝ(ns) / 10^3).
            val us = ns / BigInteger.valueOf(1_000L)

            // 5. Return 𝔽(µs).
            return us.toValue()
        }

        @JvmStatic
        @ECMAImpl("6.3.18")
        fun getEpochNanoseconds(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            // 3. Return zonedDateTime.[[Nanoseconds]].
            return thisZonedDateTime(arguments.thisValue, "get epochNanoseconds")[Slot.Nanoseconds].toValue()
        }

        @JvmStatic
        @ECMAImpl("6.3.19")
        fun getDayOfWeek(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "get dayOfWeek")

            // 3. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.TimeZone]

            // 4. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanosecond])

            // 5. Let calendar be zonedDateTime.[[Calendar]].
            val calendar = zonedDateTime[Slot.Calendar]

            // 6. Let temporalDateTime be ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar).
            val temporalDateTime = TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar)

            // 7. Return ? CalendarDayOfWeek(calendar, temporalDateTime).
            return TemporalAOs.calendarDayOfWeek(calendar, temporalDateTime)
        }

        @JvmStatic
        @ECMAImpl("6.3.20")
        fun getDayOfYear(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "get dayOfYear")

            // 3. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.TimeZone]

            // 4. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanosecond])

            // 5. Let calendar be zonedDateTime.[[Calendar]].
            val calendar = zonedDateTime[Slot.Calendar]

            // 6. Let temporalDateTime be ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar).
            val temporalDateTime = TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar)

            // 7. Return ? CalendarDayOfYear(calendar, temporalDateTime).
            return TemporalAOs.calendarDayOfYear(calendar, temporalDateTime)
        }

        @JvmStatic
        @ECMAImpl("6.3.21")
        fun getWeekOfYear(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "get weekOfYear")

            // 3. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.TimeZone]

            // 4. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanosecond])

            // 5. Let calendar be zonedDateTime.[[Calendar]].
            val calendar = zonedDateTime[Slot.Calendar]

            // 6. Let temporalDateTime be ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar).
            val temporalDateTime = TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar)

            // 7. Return ? CalendarWeekOfYear(calendar, temporalDateTime).
            return TemporalAOs.calendarWeekOfYear(calendar, temporalDateTime)
        }

        @JvmStatic
        @ECMAImpl("6.3.22")
        fun getHoursInDay(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "get hoursInDay")

            // 3. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.TimeZone]

            // 4. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanosecond])

            // 5. Let isoCalendar be ! GetISO8601Calendar().
            val isoCalendar = TemporalAOs.getISO8601Calendar()

            // 6. Let temporalDateTime be ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, isoCalendar).
            val temporalDateTime = TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, isoCalendar)

            // 7. Let year be temporalDateTime.[[ISOYear]].
            val year = temporalDateTime[Slot.ISOYear]

            // 8. Let month be temporalDateTime.[[ISOMonth]].
            val month = temporalDateTime[Slot.ISOMonth]

            // 9. Let day be temporalDateTime.[[ISODay]].
            val day = temporalDateTime[Slot.ISODay]

            // 10. Let today be ? CreateTemporalDateTime(year, month, day, 0, 0, 0, 0, 0, 0, isoCalendar).
            val today = TemporalAOs.createTemporalDateTime(year, month, day, 0, 0, 0, 0, 0, BigInteger.ZERO, isoCalendar)

            // 11. Let tomorrowFields be BalanceISODate(year, month, day + 1).
            val tomorrowFields = TemporalAOs.balanceISODate(year, month, day + 1)

            // 12. Let tomorrow be ? CreateTemporalDateTime(tomorrowFields.[[Year]], tomorrowFields.[[Month]], tomorrowFields.[[Day]], 0, 0, 0, 0, 0, 0, isoCalendar).
            val tomorrow = TemporalAOs.createTemporalDateTime(tomorrowFields.year, tomorrowFields.month, tomorrowFields.day, 0, 0, 0, 0, 0, BigInteger.ZERO, isoCalendar)

            // 13. Let todayInstant be ? BuiltinTimeZoneGetInstantFor(timeZone, today, "compatible").
            val todayInstant = TemporalAOs.builtinTimeZoneGetInstantFor(timeZone, today, "compatible")

            // 14. Let tomorrowInstant be ? BuiltinTimeZoneGetInstantFor(timeZone, tomorrow, "compatible").
            val tomorrowInstant = TemporalAOs.builtinTimeZoneGetInstantFor(timeZone, tomorrow, "compatible")

            // 15. Let diffNs be tomorrowInstant.[[Nanoseconds]] - todayInstant.[[Nanoseconds]].
            val diffNs = tomorrowInstant[Slot.Nanoseconds] - todayInstant[Slot.Nanoseconds]

            // 16. Return 𝔽(diffNs / (3.6 × 10^12)).
            return (diffNs / (36.toBigInteger() * BigInteger.TEN.pow(11))).toValue()
        }

        @JvmStatic
        @ECMAImpl("6.3.23")
        fun getDaysInWeek(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "get daysInWeek")

            // 3. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.TimeZone]

            // 4. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanosecond])

            // 5. Let calendar be zonedDateTime.[[Calendar]].
            val calendar = zonedDateTime[Slot.Calendar]

            // 6. Let temporalDateTime be ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar).
            val temporalDateTime = TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar)

            // 7. Return ? CalendarDaysInWeek(calendar, temporalDateTime).
            return TemporalAOs.calendarDaysInWeek(calendar, temporalDateTime)
        }

        @JvmStatic
        @ECMAImpl("6.3.24")
        fun getDaysInMonth(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "get daysInMonth")

            // 3. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.TimeZone]

            // 4. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanosecond])

            // 5. Let calendar be zonedDateTime.[[Calendar]].
            val calendar = zonedDateTime[Slot.Calendar]

            // 6. Let temporalDateTime be ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar).
            val temporalDateTime = TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar)

            // 7. Return ? CalendarDaysInMonth(calendar, temporalDateTime).
            return TemporalAOs.calendarDaysInMonth(calendar, temporalDateTime)
        }

        @JvmStatic
        @ECMAImpl("6.3.25")
        fun getDaysInYear(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "get DaysInYear")

            // 3. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.TimeZone]

            // 4. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanosecond])

            // 5. Let calendar be zonedDateTime.[[Calendar]].
            val calendar = zonedDateTime[Slot.Calendar]

            // 6. Let temporalDateTime be ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar).
            val temporalDateTime = TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar)

            // 7. Return ? CalendarDaysInYear(calendar, temporalDateTime).
            return TemporalAOs.calendarDaysInYear(calendar, temporalDateTime)
        }

        @JvmStatic
        @ECMAImpl("6.3.26")
        fun getMonthsInYear(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "get monthsInYear")

            // 3. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.TimeZone]

            // 4. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanosecond])

            // 5. Let calendar be zonedDateTime.[[Calendar]].
            val calendar = zonedDateTime[Slot.Calendar]

            // 6. Let temporalDateTime be ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar).
            val temporalDateTime = TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar)

            // 7. Return ? CalendarMonthsInYear(calendar, temporalDateTime).
            return TemporalAOs.calendarMonthsInYear(calendar, temporalDateTime)
        }

        @JvmStatic
        @ECMAImpl("6.3.27")
        fun getInLeapYear(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "get inLeapYear")

            // 3. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.TimeZone]

            // 4. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanosecond])

            // 5. Let calendar be zonedDateTime.[[Calendar]].
            val calendar = zonedDateTime[Slot.Calendar]

            // 6. Let temporalDateTime be ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar).
            val temporalDateTime = TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar)

            // 7. Return ? CalendarInLeapYear(calendar, temporalDateTime).
            return TemporalAOs.calendarInLeapYear(calendar, temporalDateTime)
        }

        @JvmStatic
        @ECMAImpl("6.3.28")
        fun getOffsetNanoseconds(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "get offsetNanoseconds")

            // 3. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.TimeZone]

            // 4. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanosecond])

            // 5. Return 𝔽(? GetOffsetNanosecondsFor(timeZone, instant)).
            return TemporalAOs.getOffsetNanosecondsFor(timeZone, instant).toValue()
        }

        @JvmStatic
        @ECMAImpl("6.3.29")
        fun getOffset(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "get offset")

            // 3. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanosecond])

            // 4. Return ? BuiltinTimeZoneGetOffsetStringFor(zonedDateTime.[[TimeZone]], instant).
            return TemporalAOs.builtinTimeZoneGetOffsetStringFor(zonedDateTime[Slot.TimeZone], instant).toValue()
        }

        @JvmStatic
        @ECMAImpl("6.3.30")
        fun with(arguments: JSArguments): JSValue {
            val temporalZonedDateTimeLike = arguments.argument(0)

            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "with")

            // 3. If Type(temporalZonedDateTimeLike) is not Object, then
            if (temporalZonedDateTimeLike !is JSObject) {
                // a. Throw a TypeError exception.
                Errors.TODO("ZonedDateTime.prototype.with").throwRangeError()
            }

            // 4. Perform ? RejectObjectWithCalendarOrTimeZone(temporalZonedDateTimeLike).
            TemporalAOs.rejectObjectWithCalendarOrTimeZone(temporalZonedDateTimeLike)

            // 5. Let calendar be zonedDateTime.[[Calendar]].
            val calendar = zonedDateTime[Slot.Calendar]

            // 6. Let fieldNames be ? CalendarFields(calendar, « "day", "hour", "microsecond", "millisecond", "minute", "month", "monthCode", "nanosecond", "second", "year" »).
            val fieldNames = TemporalAOs.calendarFields(calendar, listOf("day", "hour", "microsecond", "millisecond", "minute", "month", "monthCode", "nanosecond", "second", "year")).toMutableList()

            // 7. Append "offset" to fieldNames.
            fieldNames.add("offset")

            // 8. Let partialZonedDateTime be ? PrepareTemporalFields(temporalZonedDateTimeLike, fieldNames, partial).
            val partialZonedDateTime = TemporalAOs.prepareTemporalFields(temporalZonedDateTimeLike, fieldNames, null)

            // 9. Set options to ? GetOptionsObject(options).
            val options = TemporalAOs.getOptionsObject(arguments.argument(1))

            // 10. Let disambiguation be ? ToTemporalDisambiguation(options).
            val disambiguation = TemporalAOs.toTemporalDisambiguation(options)

            // 11. Let offset be ? ToTemporalOffset(options, "prefer").
            val offset = TemporalAOs.toTemporalOffset(options, "prefer")

            // 12. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.TimeZone]

            // 13. Append "timeZone" to fieldNames.
            fieldNames.add("timeZone")

            // 14. Let fields be ? PrepareTemporalFields(zonedDateTime, fieldNames, « "timeZone", "offset" »).
            var fields = TemporalAOs.prepareTemporalFields(zonedDateTime, fieldNames, setOf("timeZone", "offset"))

            // 15. Set fields to ? CalendarMergeFields(calendar, fields, partialZonedDateTime).
            fields = TemporalAOs.calendarMergeFields(calendar, fields, partialZonedDateTime)

            // 16. Set fields to ? PrepareTemporalFields(fields, fieldNames, « "timeZone", "offset" »).
            fields = TemporalAOs.prepareTemporalFields(fields, fieldNames, setOf("timeZone", "offset"))

            // 17. Let offsetString be ! Get(fields, "offset").
            val offsetString = fields.get("offset")

            // 18. Assert: Type(offsetString) is String.
            ecmaAssert(offsetString is JSString)

            // 19. Let dateTimeResult be ? InterpretTemporalDateTimeFields(calendar, fields, options).
            val dateTimeResult = TemporalAOs.interpretTemporalDateTimeFields(calendar, fields, options)

            // 20. Let offsetNanoseconds be ? ParseTimeZoneOffsetString(offsetString).
            val offsetNanoseconds = TemporalAOs.parseTimeZoneOffsetString(offsetString.string)

            // 21. Let epochNanoseconds be ? InterpretISODateTimeOffset(dateTimeResult.[[Year]], dateTimeResult.[[Month]], dateTimeResult.[[Day]], dateTimeResult.[[Hour]], dateTimeResult.[[Minute]], dateTimeResult.[[Second]], dateTimeResult.[[Millisecond]], dateTimeResult.[[Microsecond]], dateTimeResult.[[Nanosecond]], option, offsetNanoseconds, timeZone, disambiguation, offset, match exactly).
            val epochNanoseconds = TemporalAOs.interpretISODateTimeOffset(
                dateTimeResult.year,
                dateTimeResult.month,
                dateTimeResult.day,
                dateTimeResult.hour,
                dateTimeResult.minute,
                dateTimeResult.second,
                dateTimeResult.millisecond,
                dateTimeResult.microsecond,
                dateTimeResult.nanosecond,
                "option",
                offsetNanoseconds,
                timeZone,
                disambiguation,
                offset,
                "match exactly",
            )

            // 22. Return ! CreateTemporalZonedDateTime(epochNanoseconds, timeZone, calendar).
            return TemporalAOs.createTemporalZonedDateTime(epochNanoseconds, timeZone, calendar)
        }

        @JvmStatic
        @ECMAImpl("6.3.31")
        fun withPlainTime(arguments: JSArguments): JSValue {
            val plainTimeLike = arguments.argument(0)

            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "withPlainTime")

            // 3. If plainTimeLike is undefined, then
            val plainTime = if (plainTimeLike == JSUndefined) {
                // a. Let plainTime be ! CreateTemporalTime(0, 0, 0, 0, 0, 0).
                TemporalAOs.createTemporalTime(0, 0, 0, 0, 0, BigInteger.ZERO)
            }
            // 4. Else,
            else {
                // a. Let plainTime be ? ToTemporalTime(plainTimeLike).
                TemporalAOs.toTemporalTime(plainTimeLike)
            }

            // 5. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.TimeZone]

            // 6. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            var instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanoseconds])

            // 7. Let calendar be zonedDateTime.[[Calendar]].
            val calendar = zonedDateTime[Slot.Calendar]

            // 8. Let plainDateTime be ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar).
            val plainDateTime = TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar)

            // 9. Let resultPlainDateTime be ? CreateTemporalDateTime(plainDateTime.[[ISOYear]], plainDateTime.[[ISOMonth]], plainDateTime.[[ISODay]], plainTime.[[ISOHour]], plainTime.[[ISOMinute]], plainTime.[[ISOSecond]], plainTime.[[ISOMillisecond]], plainTime.[[ISOMicrosecond]], plainTime.[[ISONanosecond]], calendar).
            val resultPlainDateTime = TemporalAOs.createTemporalDateTime(
                plainDateTime[Slot.ISOYear],
                plainDateTime[Slot.ISOMonth],
                plainDateTime[Slot.ISODay],
                plainTime[Slot.ISOHour],
                plainTime[Slot.ISOMinute],
                plainTime[Slot.ISOSecond],
                plainTime[Slot.ISOMillisecond],
                plainTime[Slot.ISOMicrosecond],
                plainTime[Slot.ISONanosecond],
                calendar
            )

            // 10. Set instant to ? BuiltinTimeZoneGetInstantFor(timeZone, resultPlainDateTime, "compatible").
            instant = TemporalAOs.builtinTimeZoneGetInstantFor(timeZone, resultPlainDateTime, "compatible")

            // 11. Return ! CreateTemporalZonedDateTime(instant.[[Nanoseconds]], timeZone, calendar).
            return TemporalAOs.createTemporalZonedDateTime(instant[Slot.Nanoseconds], timeZone, calendar)
        }

        @JvmStatic
        @ECMAImpl("6.3.32")
        fun withPlainDate(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "withPlainDate")

            // 3. Let plainDate be ? ToTemporalDate(plainDateLike).
            val plainDate = TemporalAOs.toTemporalDate(arguments.argument(0))

            // 4. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.TimeZone]

            // 5. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            var instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanoseconds])

            // 6. Let plainDateTime be ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, zonedDateTime.[[Calendar]]).
            val plainDateTime = TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, zonedDateTime[Slot.Calendar])

            // 7. Let calendar be ? ConsolidateCalendars(zonedDateTime.[[Calendar]], plainDate.[[Calendar]]).
            val calendar = TemporalAOs.consolidateCalendars(zonedDateTime[Slot.Calendar], plainDate[Slot.Calendar])

            // 8. Let resultPlainDateTime be ? CreateTemporalDateTime(plainDate.[[ISOYear]], plainDate.[[ISOMonth]], plainDate.[[ISODay]], plainDateTime.[[ISOHour]], plainDateTime.[[ISOMinute]], plainDateTime.[[ISOSecond]], plainDateTime.[[ISOMillisecond]], plainDateTime.[[ISOMicrosecond]], plainDateTime.[[ISONanosecond]], calendar).
            val resultPlainDateTime = TemporalAOs.createTemporalDateTime(
                plainDate[Slot.ISOYear],
                plainDate[Slot.ISOMonth],
                plainDate[Slot.ISODay],
                plainDateTime[Slot.ISOHour],
                plainDateTime[Slot.ISOMinute],
                plainDateTime[Slot.ISOSecond],
                plainDateTime[Slot.ISOMillisecond],
                plainDateTime[Slot.ISOMicrosecond],
                plainDateTime[Slot.ISONanosecond],
                calendar
            )

            // 9. Set instant to ? BuiltinTimeZoneGetInstantFor(timeZone, resultPlainDateTime, "compatible").
            instant = TemporalAOs.builtinTimeZoneGetInstantFor(timeZone, resultPlainDateTime, "compatible")

            // 10. Return ! CreateTemporalZonedDateTime(instant.[[Nanoseconds]], timeZone, calendar).
            return TemporalAOs.createTemporalZonedDateTime(instant[Slot.Nanoseconds], timeZone, calendar)
        }

        @JvmStatic
        @ECMAImpl("6.3.33")
        fun withTimeZone(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "withTimeZone")

            // 3. Let timeZone be ? ToTemporalTimeZone(timeZoneLike).
            val timeZone = TemporalAOs.toTemporalTimeZone(arguments.argument(0))

            // 4. Return ! CreateTemporalZonedDateTime(zonedDateTime.[[Nanoseconds]], timeZone, zonedDateTime.[[Calendar]]).
            return TemporalAOs.createTemporalZonedDateTime(zonedDateTime[Slot.Nanoseconds], timeZone, zonedDateTime[Slot.Calendar])
        }

        @JvmStatic
        @ECMAImpl("6.3.34")
        fun withCalendar(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "withCalendar")

            // 3. Let calendar be ? ToTemporalCalendar(calendarLike).
            val calendar = TemporalAOs.toTemporalCalendar(arguments.argument(0))

            // 4. Return ! CreateTemporalZonedDateTime(zonedDateTime.[[Nanoseconds]], zonedDateTime.[[TimeZone]], calendar).
            return TemporalAOs.createTemporalZonedDateTime(zonedDateTime[Slot.Nanoseconds], zonedDateTime[Slot.TimeZone], calendar)
        }

        @JvmStatic
        @ECMAImpl("6.3.35")
        fun add(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "add")

            // 3. Return ? AddDurationToOrSubtractDurationFromZonedDateTime(add, zonedDateTime, temporalDurationLike, options).
            return TemporalAOs.addDurationToOrSubtractDurationFromZonedDateTime(true, zonedDateTime, arguments.argument(0), arguments.argument(1))
        }

        @JvmStatic
        @ECMAImpl("6.3.36")
        fun subtract(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "subtract")

            // 3. Return ? AddDurationToOrSubtractDurationFromZonedDateTime(subtract, zonedDateTime, temporalDurationLike, options).
            return TemporalAOs.addDurationToOrSubtractDurationFromZonedDateTime(false, zonedDateTime, arguments.argument(0), arguments.argument(1))
        }

        @JvmStatic
        @ECMAImpl("6.3.37")
        fun until(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "until")

            // 3. Return ? DifferenceTemporalZonedDateTime(until, zonedDateTime, other, options).
            return TemporalAOs.differenceTemporalZonedDateTime(true, zonedDateTime, arguments.argument(0), arguments.argument(1))
        }

        @JvmStatic
        @ECMAImpl("6.3.38")
        fun since(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "since")

            // 3. Return ? DifferenceTemporalZonedDateTime(since, zonedDateTime, other, options).
            return TemporalAOs.differenceTemporalZonedDateTime(false, zonedDateTime, arguments.argument(0), arguments.argument(1))
        }

        @JvmStatic
        @ECMAImpl("6.3.39")
        fun round(arguments: JSArguments): JSValue {
            var roundTo = arguments.argument(0)

            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "round")

            // 3. If roundTo is undefined, then
            if (roundTo == JSUndefined) {
                // a. Throw a TypeError exception.
                Errors.TODO("ZonedDateTime.prototype.round 1").throwTypeError()
            }

            // 4. If Type(roundTo) is String, then
            if (roundTo is JSString) {
                // a. Let paramString be roundTo.
                val paramString = roundTo
                // b. Set roundTo to OrdinaryObjectCreate(null).
                roundTo = JSObject.create()

                // c. Perform ! CreateDataPropertyOrThrow(roundTo, "smallestUnit", paramString).
                AOs.createDataPropertyOrThrow(roundTo, "smallestUnit".key(), paramString)
            }
            // 5. Else,
            else {
                // a. Set roundTo to ? GetOptionsObject(roundTo).
                roundTo = TemporalAOs.getOptionsObject(roundTo)
            }

            // 6. Let smallestUnit be ? GetTemporalUnit(roundTo, "smallestUnit", time, required, « "day" »).
            val smallestUnit = TemporalAOs.getTemporalUnit(roundTo, "smallestUnit".key(), "time", TemporalAOs.TemporalUnitDefault.Required, listOf("day"))!!

            // 7. Let roundingMode be ? ToTemporalRoundingMode(roundTo, "halfExpand").
            val roundingMode = TemporalAOs.toTemporalRoundingMode(roundTo, "halfExpand")

            // 8. Let roundingIncrement be ? ToTemporalDateTimeRoundingIncrement(roundTo, smallestUnit).
            val roundingIncrement = TemporalAOs.toTemporalDateTimeRoundingIncrement(roundTo, smallestUnit)

            // 9. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.TimeZone]

            // 10. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanoseconds])

            // 11. Let calendar be zonedDateTime.[[Calendar]].
            val calendar = zonedDateTime[Slot.Calendar]

            // 12. Let temporalDateTime be ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar).
            val temporalDateTime = TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar)

            // 13. Let isoCalendar be ! GetISO8601Calendar().
            val isoCalendar = TemporalAOs.getISO8601Calendar()

            // 14. Let dtStart be ? CreateTemporalDateTime(temporalDateTime.[[ISOYear]], temporalDateTime.[[ISOMonth]], temporalDateTime.[[ISODay]], 0, 0, 0, 0, 0, 0, isoCalendar).
            val dtStart = TemporalAOs.createTemporalDateTime(temporalDateTime[Slot.ISOYear], temporalDateTime[Slot.ISOMonth], temporalDateTime[Slot.ISODay], 0, 0, 0, 0, 0, BigInteger.ZERO, isoCalendar)

            // 15. Let instantStart be ? BuiltinTimeZoneGetInstantFor(timeZone, dtStart, "compatible").
            val instantStart = TemporalAOs.builtinTimeZoneGetInstantFor(timeZone, dtStart, "compatible")

            // 16. Let startNs be instantStart.[[Nanoseconds]].
            val startNs = instantStart[Slot.Nanoseconds]

            // 17. Let endNs be ? AddZonedDateTime(startNs, timeZone, calendar, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0).
            val endNs = TemporalAOs.addZonedDateTime(startNs, timeZone, calendar, 0, 0, 0, 1, 0, 0, 0, 0, 0, BigInteger.ZERO)

            // 18. Let dayLengthNs be ℝ(endNs - startNs).
            val dayLengthNs = endNs - startNs

            // 19. If dayLengthNs ≤ 0, then
            if (dayLengthNs <= BigInteger.ZERO) {
                // a. Throw a RangeError exception.
                Errors.TODO("ZonedDateTime.prototype.round 2").throwTypeError()
            }

            // 20. Let roundResult be ! RoundISODateTime(temporalDateTime.[[ISOYear]], temporalDateTime.[[ISOMonth]], temporalDateTime.[[ISODay]], temporalDateTime.[[ISOHour]], temporalDateTime.[[ISOMinute]], temporalDateTime.[[ISOSecond]], temporalDateTime.[[ISOMillisecond]], temporalDateTime.[[ISOMicrosecond]], temporalDateTime.[[ISONanosecond]], roundingIncrement, smallestUnit, roundingMode, dayLengthNs).
            val roundResult = TemporalAOs.roundISODateTime(
                temporalDateTime[Slot.ISOYear],
                temporalDateTime[Slot.ISOMonth],
                temporalDateTime[Slot.ISODay],
                temporalDateTime[Slot.ISOHour],
                temporalDateTime[Slot.ISOMinute],
                temporalDateTime[Slot.ISOSecond],
                temporalDateTime[Slot.ISOMillisecond],
                temporalDateTime[Slot.ISOMicrosecond],
                temporalDateTime[Slot.ISONanosecond],
                roundingIncrement,
                smallestUnit,
                roundingMode,
                dayLengthNs,
            )

            // 21. Let offsetNanoseconds be ? GetOffsetNanosecondsFor(timeZone, instant).
            val offsetNanoseconds = TemporalAOs.getOffsetNanosecondsFor(timeZone, instant)

            // 22. Let epochNanoseconds be ? InterpretISODateTimeOffset(roundResult.[[Year]], roundResult.[[Month]], roundResult.[[Day]], roundResult.[[Hour]], roundResult.[[Minute]], roundResult.[[Second]], roundResult.[[Millisecond]], roundResult.[[Microsecond]], roundResult.[[Nanosecond]], option, offsetNanoseconds, timeZone, "compatible", "prefer", match exactly).
            val epochNanoseconds = TemporalAOs.interpretISODateTimeOffset(
                roundResult.year,
                roundResult.month,
                roundResult.day,
                roundResult.hour,
                roundResult.minute,
                roundResult.second,
                roundResult.millisecond,
                roundResult.microsecond,
                roundResult.nanosecond,
                "option",
                offsetNanoseconds,
                timeZone,
                "compatible",
                "prefer",
                "match exactly",
            )

            // 23. Return ! CreateTemporalZonedDateTime(epochNanoseconds, timeZone, calendar).
            return TemporalAOs.createTemporalZonedDateTime(epochNanoseconds, timeZone, calendar)
        }

        @JvmStatic
        @ECMAImpl("6.3.40")
        fun equals(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "equals")

            // 3. Set other to ? ToTemporalZonedDateTime(other).
            val other = TemporalAOs.toTemporalZonedDateTime(arguments.argument(0))

            // 4. If zonedDateTime.[[Nanoseconds]] ≠ other.[[Nanoseconds]], return false.
            if (zonedDateTime[Slot.Nanoseconds] != other[Slot.Nanoseconds])
                return JSFalse

            // 5. If ? TimeZoneEquals(zonedDateTime.[[TimeZone]], other.[[TimeZone]]) is false, return false.
            if (!TemporalAOs.timeZoneEquals(zonedDateTime[Slot.TimeZone], other[Slot.TimeZone]))
                return JSFalse

            // 6. Return ? CalendarEquals(zonedDateTime.[[Calendar]], other.[[Calendar]]).
            return TemporalAOs.calendarEquals(zonedDateTime[Slot.Calendar], other[Slot.Calendar]).toValue()
        }

        @JvmStatic
        @ECMAImpl("6.3.41")
        fun toString(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "toString")

            // 3. Set options to ? GetOptionsObject(options).
            val options = TemporalAOs.getOptionsObject(arguments.argument(0))

            // 4. Let precision be ? ToSecondsStringPrecision(options).
            val precision = TemporalAOs.toSecondsStringPrecision(options)

            // 5. Let roundingMode be ? ToTemporalRoundingMode(options, "trunc").
            val roundingMode = TemporalAOs.toTemporalRoundingMode(options, "trunc")

            // 6. Let showCalendar be ? ToShowCalendarOption(options).
            val showCalendar = TemporalAOs.toShowCalendarOption(options)

            // 7. Let showTimeZone be ? ToShowTimeZoneNameOption(options).
            val showTimeZone = TemporalAOs.toShowTimeZoneNameOption(options)

            // 8. Let showOffset be ? ToShowOffsetOption(options).
            val showOffset = TemporalAOs.toShowOffsetOption(options)

            // 9. Return ? TemporalZonedDateTimeToString(zonedDateTime, precision.[[Precision]], showCalendar, showTimeZone, showOffset, precision.[[Increment]], precision.[[Unit]], roundingMode).
            return TemporalAOs.temporalZonedDateTimeToString(zonedDateTime, precision.precision, showCalendar, showTimeZone, showOffset, precision.increment, precision.unit, roundingMode).toValue()
        }

        @JvmStatic
        @ECMAImpl("6.3.42")
        fun toLocaleString(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "toLocaleString")

            // 3. Return ? TemporalZonedDateTimeToString(zonedDateTime, "auto", "auto", "auto", "auto").
            return TemporalAOs.temporalZonedDateTimeToString(zonedDateTime, "auto", "auto", "auto", "auto").toValue()
        }

        @JvmStatic
        @ECMAImpl("6.3.43")
        fun toJSON(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "toJSON")

            // 3. Return ? TemporalZonedDateTimeToString(zonedDateTime, "auto", "auto", "auto", "auto").
            return TemporalAOs.temporalZonedDateTimeToString(zonedDateTime, "auto", "auto", "auto", "auto").toValue()
        }

        @JvmStatic
        @ECMAImpl("6.3.44")
        fun valueOf(arguments: JSArguments): JSValue {
            // 1. Throw a TypeError exception.
            Errors.TODO("ZonedDateTime.prototype.valueOf").throwTypeError()
        }

        @JvmStatic
        @ECMAImpl("6.3.45")
        fun startOfDay(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "startOfDay")

            // 3. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.TimeZone]

            // 4. Let calendar be zonedDateTime.[[Calendar]].
            val calendar = zonedDateTime[Slot.Calendar]

            // 5. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanoseconds])

            // 6. Let temporalDateTime be ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar).
            val temporalDateTime = TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar)

            // 7. Let startDateTime be ? CreateTemporalDateTime(temporalDateTime.[[ISOYear]], temporalDateTime.[[ISOMonth]], temporalDateTime.[[ISODay]], 0, 0, 0, 0, 0, 0, calendar).
            val startDateTime = TemporalAOs.createTemporalDateTime(temporalDateTime[Slot.ISOYear], temporalDateTime[Slot.ISOMonth], temporalDateTime[Slot.ISODay], 0, 0, 0, 0, 0, BigInteger.ZERO, calendar)

            // 8. Let startInstant be ? BuiltinTimeZoneGetInstantFor(timeZone, startDateTime, "compatible").
            val startInstant = TemporalAOs.builtinTimeZoneGetInstantFor(timeZone, startDateTime, "compatible")

            // 9. Return ! CreateTemporalZonedDateTime(startInstant.[[Nanoseconds]], timeZone, calendar).
            return TemporalAOs.createTemporalZonedDateTime(startInstant[Slot.Nanoseconds], timeZone, calendar)
        }

        @JvmStatic
        @ECMAImpl("6.3.46")
        fun toInstant(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "toInstant")

            // 3. Return ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            return TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanoseconds])
        }

        @JvmStatic
        @ECMAImpl("6.3.47")
        fun toPlainDate(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "toPlainDate")

            // 3. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.Calendar]

            // 4. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanoseconds])

            // 5. Let calendar be zonedDateTime.[[Calendar]].
            val calendar = zonedDateTime[Slot.Calendar]
            
            // 6. Let temporalDateTime be ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar).
            val temporalDateTime = TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar)

            // 7. Return ! CreateTemporalDate(temporalDateTime.[[ISOYear]], temporalDateTime.[[ISOMonth]], temporalDateTime.[[ISODay]], calendar).
            return TemporalAOs.createTemporalDate(temporalDateTime[Slot.ISOYear], temporalDateTime[Slot.ISOMonth], temporalDateTime[Slot.ISODay], calendar)
        }

        @JvmStatic
        @ECMAImpl("6.3.48")
        fun toPlainTime(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "toPlainTime")

            // 3. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.Calendar]

            // 4. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanoseconds])

            // 5. Let calendar be zonedDateTime.[[Calendar]].
            val calendar = zonedDateTime[Slot.Calendar]

            // 6. Let temporalDateTime be ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar).
            val temporalDateTime = TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar)

            // 7. Return ! CreateTemporalTime(temporalDateTime.[[ISOHour]], temporalDateTime.[[ISOMinute]], temporalDateTime.[[ISOSecond]], temporalDateTime.[[ISOMillisecond]], temporalDateTime.[[ISOMicrosecond]], temporalDateTime.[[ISONanosecond]]).
            return TemporalAOs.createTemporalTime(
                temporalDateTime[Slot.ISOHour],
                temporalDateTime[Slot.ISOMinute],
                temporalDateTime[Slot.ISOSecond],
                temporalDateTime[Slot.ISOMillisecond],
                temporalDateTime[Slot.ISOMicrosecond],
                temporalDateTime[Slot.ISONanosecond],
            )
        }

        @JvmStatic
        @ECMAImpl("6.3.49")
        fun toPlainDateTime(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "toPlainDateTime")

            // 3. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.Calendar]

            // 4. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanoseconds])

            // 5. Return ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, zonedDateTime.[[Calendar]]).
            return TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, zonedDateTime[Slot.Calendar])
        }

        @JvmStatic
        @ECMAImpl("6.3.50")
        fun toPlainYearMonth(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "toPlainYearMonth")

            // 3. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.Calendar]

            // 4. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanoseconds])

            // 5. Let calendar be zonedDateTime.[[Calendar]].
            val calendar = zonedDateTime[Slot.Calendar]

            // 6. Let temporalDateTime be ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar).
            val temporalDateTime = TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar)

            // 7. Let fieldNames be ? CalendarFields(calendar, « "monthCode", "year" »).
            val fieldNames = TemporalAOs.calendarFields(calendar, listOf("monthCode", "year"))

            // 8. Let fields be ? PrepareTemporalFields(temporalDateTime, fieldNames, «»).
            val fields = TemporalAOs.prepareTemporalFields(temporalDateTime, fieldNames, emptySet())

            // 9. Return ? CalendarYearMonthFromFields(calendar, fields).
            return TemporalAOs.calendarYearMonthFromFields(calendar, fields)
        }

        @JvmStatic
        @ECMAImpl("6.3.51")
        fun toPlainMonthDay(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "toPlainMonthDay")

            // 3. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.Calendar]

            // 4. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanoseconds])

            // 5. Let calendar be zonedDateTime.[[Calendar]].
            val calendar = zonedDateTime[Slot.Calendar]

            // 6. Let temporalDateTime be ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar).
            val temporalDateTime = TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar)

            // 7. Let fieldNames be ? CalendarFields(calendar, « "monthCode", "year" »).
            val fieldNames = TemporalAOs.calendarFields(calendar, listOf("day", "monthCode"))

            // 8. Let fields be ? PrepareTemporalFields(temporalDateTime, fieldNames, «»).
            val fields = TemporalAOs.prepareTemporalFields(temporalDateTime, fieldNames, emptySet())

            // 9. Return ? CalendarYearMonthFromFields(calendar, fields).
            return TemporalAOs.calendarMonthDayFromFields(calendar, fields)
        }

        @JvmStatic
        @ECMAImpl("6.3.52")
        fun getISOFields(arguments: JSArguments): JSValue {
            // 1. Let zonedDateTime be the this value.
            // 2. Perform ? RequireInternalSlot(zonedDateTime, [[InitializedTemporalZonedDateTime]]).
            val zonedDateTime = thisZonedDateTime(arguments.thisValue, "getISOFields")

            // 3. Let fields be OrdinaryObjectCreate(%Object.prototype%).
            val fields = JSObject.create()

            // 4. Let timeZone be zonedDateTime.[[TimeZone]].
            val timeZone = zonedDateTime[Slot.Calendar]

            // 5. Let instant be ! CreateTemporalInstant(zonedDateTime.[[Nanoseconds]]).
            val instant = TemporalAOs.createTemporalInstant(zonedDateTime[Slot.Nanoseconds])

            // 6. Let calendar be zonedDateTime.[[Calendar]].
            val calendar = zonedDateTime[Slot.Calendar]

            // 7. Let dateTime be ? BuiltinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar).
            val dateTime = TemporalAOs.builtinTimeZoneGetPlainDateTimeFor(timeZone, instant, calendar)

            // 8. Let offset be ? BuiltinTimeZoneGetOffsetStringFor(timeZone, instant).
            val offset = TemporalAOs.builtinTimeZoneGetOffsetStringFor(timeZone, instant)

            // 9. Perform ! CreateDataPropertyOrThrow(fields, "calendar", calendar).
            AOs.createDataPropertyOrThrow(fields, "calendar".key(), calendar)

            // 10. Perform ! CreateDataPropertyOrThrow(fields, "isoDay", 𝔽(dateTime.[[ISODay]])).
            AOs.createDataPropertyOrThrow(fields, "isoDay".key(), dateTime[Slot.ISODay].toValue())

            // 11. Perform ! CreateDataPropertyOrThrow(fields, "isoHour", 𝔽(dateTime.[[ISOHour]])).
            AOs.createDataPropertyOrThrow(fields, "isoHour".key(), dateTime[Slot.ISOHour].toValue())

            // 12. Perform ! CreateDataPropertyOrThrow(fields, "isoMicrosecond", 𝔽(dateTime.[[ISOMicrosecond]])).
            AOs.createDataPropertyOrThrow(fields, "isoMicrosecond".key(), dateTime[Slot.ISOMicrosecond].toValue())

            // 13. Perform ! CreateDataPropertyOrThrow(fields, "isoMillisecond", 𝔽(dateTime.[[ISOMillisecond]])).
            AOs.createDataPropertyOrThrow(fields, "isoMillisecond".key(), dateTime[Slot.ISOMillisecond].toValue())

            // 14. Perform ! CreateDataPropertyOrThrow(fields, "isoMinute", 𝔽(dateTime.[[ISOMinute]])).
            AOs.createDataPropertyOrThrow(fields, "isoMinute".key(), dateTime[Slot.ISOMinute].toValue())

            // 15. Perform ! CreateDataPropertyOrThrow(fields, "isoMonth", 𝔽(dateTime.[[ISOMonth]])).
            AOs.createDataPropertyOrThrow(fields, "isoMonth".key(), dateTime[Slot.ISOMonth].toValue())

            // 16. Perform ! CreateDataPropertyOrThrow(fields, "isoNanosecond", 𝔽(dateTime.[[ISONanosecond]])).
            AOs.createDataPropertyOrThrow(fields, "isoNanosecond".key(), dateTime[Slot.ISONanosecond].toValue())

            // 17. Perform ! CreateDataPropertyOrThrow(fields, "isoSecond", 𝔽(dateTime.[[ISOSecond]])).
            AOs.createDataPropertyOrThrow(fields, "isoSecond".key(), dateTime[Slot.ISOSecond].toValue())

            // 18. Perform ! CreateDataPropertyOrThrow(fields, "isoYear", 𝔽(dateTime.[[ISOYear]])).
            AOs.createDataPropertyOrThrow(fields, "isoYear".key(), dateTime[Slot.ISOYear].toValue())

            // 19. Perform ! CreateDataPropertyOrThrow(fields, "offset", offset).
            AOs.createDataPropertyOrThrow(fields, "offset".key(), offset.toValue())

            // 20. Perform ! CreateDataPropertyOrThrow(fields, "timeZone", timeZone).
            AOs.createDataPropertyOrThrow(fields, "timeZone".key(), timeZone)

            // 21. Return fields.
            return fields
        }
    }
}