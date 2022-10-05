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

class JSPlainDateTimeProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("constructor", realm.plainDateTimeCtor)
        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "Temporal.PlainDateTime".toValue(), attrs { +conf })

        defineBuiltinGetter("calendar", ::getCalendar)
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
        defineBuiltinGetter("dayOfWeek", ::getDayOfWeek)
        defineBuiltinGetter("dayOfYear", ::getDayOfYear)
        defineBuiltinGetter("weekOfYear", ::getWeekOfYear)
        defineBuiltinGetter("daysInWeek", ::getDaysInWeek)
        defineBuiltinGetter("daysInMonth", ::getDaysInMonth)
        defineBuiltinGetter("daysInYear", ::getDaysInYear)
        defineBuiltinGetter("monthsInYear", ::getMonthsInYear)
        defineBuiltinGetter("inLeapYear", ::getInLeapYear)

        defineBuiltin("with", 1, ::with)
        defineBuiltin("withPlainTime", 0, ::withPlainTime)
        defineBuiltin("withPlainDate", 0, ::withPlainDate)
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
        defineBuiltin("toZonedDateTime", 0, ::toZonedDateTime)
        defineBuiltin("toPlainDate", 0, ::toPlainDate)
        defineBuiltin("toPlainYearMonth", 0, ::toPlainYearMonth)
        defineBuiltin("toPlainMonthDay", 0, ::toPlainMonthDay)
        defineBuiltin("toPlainTime", 0, ::toPlainTime)
        defineBuiltin("getISOFields", 0, ::getISOFields)
    }

    companion object {
        @JvmStatic
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSPlainDateTimeProto(realm).initialize()

        private fun thisPlainDateTime(thisValue: JSValue, method: String): JSObject {
            if (!AOs.requireInternalSlot(thisValue, Slot.InitializedTemporalDateTime))
                Errors.IncompatibleMethodCall("PlainDateTime.prototype.$method").throwTypeError()
            return thisValue
        }

        @JvmStatic
        @ECMAImpl("5.3.3")
        fun getCalendar(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            // 3. Return dateTime.[[Calendar]].
            return thisPlainDateTime(arguments.thisValue, "get calendar")[Slot.Calendar].toValue()
        }

        @JvmStatic
        @ECMAImpl("5.3.4")
        fun getYear(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "get year")

            // 3. Let calendar be dateTime.[[Calendar]].
            val calendar = dateTime[Slot.Calendar]

            // 4. Return ? CalendarYear(calendar, dateTime).
            return TemporalAOs.calendarYear(calendar, dateTime)
        }

        @JvmStatic
        @ECMAImpl("5.3.5")
        fun getMonth(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "get month")

            // 3. Let calendar be dateTime.[[Calendar]].
            val calendar = dateTime[Slot.Calendar]

            // 4. Return ? CalendarMonth(calendar, dateTime).
            return TemporalAOs.calendarMonth(calendar, dateTime)
        }

        @JvmStatic
        @ECMAImpl("5.3.6")
        fun getMonthCode(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "get monthCode")

            // 3. Let calendar be dateTime.[[Calendar]].
            val calendar = dateTime[Slot.Calendar]

            // 4. Return ? CalendarMonthCode(calendar, dateTime).
            return TemporalAOs.calendarMonthCode(calendar, dateTime)
        }

        @JvmStatic
        @ECMAImpl("5.3.7")
        fun getDay(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "get day")

            // 3. Let calendar be dateTime.[[Calendar]].
            val calendar = dateTime[Slot.Calendar]

            // 4. Return ? CalendarDay(calendar, dateTime).
            return TemporalAOs.calendarDay(calendar, dateTime)
        }

        @JvmStatic
        @ECMAImpl("5.3.8")
        fun getHour(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            // 3. Return 𝔽(dateTime.[[ISOHour]]).
            return thisPlainDateTime(arguments.thisValue, "get hour")[Slot.ISOHour].toValue()
        }

        @JvmStatic
        @ECMAImpl("5.3.9")
        fun getMinute(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            // 3. Return 𝔽(dateTime.[[ISOMinute]]).
            return thisPlainDateTime(arguments.thisValue, "get minute")[Slot.ISOMinute].toValue()
        }

        @JvmStatic
        @ECMAImpl("5.3.10")
        fun getSecond(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            // 3. Return 𝔽(dateTime.[[ISOSecond]]).
            return thisPlainDateTime(arguments.thisValue, "get second")[Slot.ISOSecond].toValue()
        }

        @JvmStatic
        @ECMAImpl("5.3.11")
        fun getMillisecond(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            // 3. Return 𝔽(dateTime.[[ISOMillisecond]]).
            return thisPlainDateTime(arguments.thisValue, "get millisecond")[Slot.ISOMillisecond].toValue()
        }

        @JvmStatic
        @ECMAImpl("5.3.12")
        fun getMicrosecond(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            // 3. Return 𝔽(dateTime.[[ISOMicrosecond]]).
            return thisPlainDateTime(arguments.thisValue, "get microsecond")[Slot.ISOMicrosecond].toValue()
        }

        @JvmStatic
        @ECMAImpl("5.3.13")
        fun getNanosecond(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            // 3. Return 𝔽(dateTime.[[ISONanosecond]]).
            return thisPlainDateTime(arguments.thisValue, "get nanosecond")[Slot.ISONanosecond].toValue()
        }

        @JvmStatic
        @ECMAImpl("5.3.14")
        fun getDayOfWeek(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "get dayOfWeek")

            // 3. Let calendar be dateTime.[[Calendar]].
            val calendar = dateTime[Slot.Calendar]

            // 4. Return ? CalendarDayOfWeek(calendar, dateTime).
            return TemporalAOs.calendarDayOfWeek(calendar, dateTime)
        }

        @JvmStatic
        @ECMAImpl("5.3.15")
        fun getDayOfYear(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "get dayOfYear")

            // 3. Let calendar be dateTime.[[Calendar]].
            val calendar = dateTime[Slot.Calendar]

            // 4. Return ? CalendarDayOfYear(calendar, dateTime).
            return TemporalAOs.calendarDayOfYear(calendar, dateTime)
        }

        @JvmStatic
        @ECMAImpl("5.3.16")
        fun getWeekOfYear(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "get weekOfYear")

            // 3. Let calendar be dateTime.[[Calendar]].
            val calendar = dateTime[Slot.Calendar]

            // 4. Return ? CalendarWeekOfYear(calendar, dateTime).
            return TemporalAOs.calendarWeekOfYear(calendar, dateTime)
        }

        @JvmStatic
        @ECMAImpl("5.3.17")
        fun getDaysInWeek(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "get daysInWeek")

            // 3. Let calendar be dateTime.[[Calendar]].
            val calendar = dateTime[Slot.Calendar]

            // 4. Return ? CalendarDasyInWeek(calendar, dateTime).
            return TemporalAOs.calendarDaysInWeek(calendar, dateTime)
        }

        @JvmStatic
        @ECMAImpl("5.3.18")
        fun getDaysInMonth(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "get daysInMonth")

            // 3. Let calendar be dateTime.[[Calendar]].
            val calendar = dateTime[Slot.Calendar]

            // 4. Return ? CalendarDaysInMonth(calendar, dateTime).
            return TemporalAOs.calendarDaysInMonth(calendar, dateTime)
        }

        @JvmStatic
        @ECMAImpl("5.3.19")
        fun getDaysInYear(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "get daysInYear")

            // 3. Let calendar be dateTime.[[Calendar]].
            val calendar = dateTime[Slot.Calendar]

            // 4. Return ? CalendarDaysInYear(calendar, dateTime).
            return TemporalAOs.calendarDaysInYear(calendar, dateTime)
        }

        @JvmStatic
        @ECMAImpl("5.3.20")
        fun getMonthsInYear(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "get monthsInYear")

            // 3. Let calendar be dateTime.[[Calendar]].
            val calendar = dateTime[Slot.Calendar]

            // 4. Return ? CalendarMonthsInYear(calendar, dateTime).
            return TemporalAOs.calendarMonthsInYear(calendar, dateTime)
        }

        @JvmStatic
        @ECMAImpl("5.3.21")
        fun getInLeapYear(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "get inLeapYear")

            // 3. Let calendar be dateTime.[[Calendar]].
            val calendar = dateTime[Slot.Calendar]

            // 4. Return ? CalendarInLeapYear(calendar, dateTime).
            return TemporalAOs.calendarInLeapYear(calendar, dateTime)
        }

        @JvmStatic
        @ECMAImpl("5.3.22")
        fun with(arguments: JSArguments): JSValue {
            val temporalDateTimeLike = arguments.argument(0)

            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "with")

            // 3. If Type(temporalDateTimeLike) is not Object, then
            if (temporalDateTimeLike !is JSObject) {
                // a. Throw a TypeError exception.
                Errors.TODO("PlainDateTime.prototype.with").throwTypeError()
            }

            // 4. Perform ? RejectObjectWithCalendarOrTimeZone(temporalDateTimeLike).
            TemporalAOs.rejectObjectWithCalendarOrTimeZone(temporalDateTimeLike)

            // 5. Let calendar be dateTime.[[Calendar]].
            val calendar = dateTime[Slot.Calendar]

            // 6. Let fieldNames be ? CalendarFields(calendar, « "day", "hour", "microsecond", "millisecond", "minute", "month", "monthCode", "nanosecond", "second", "year" »).
            val fieldNames = TemporalAOs.calendarFields(calendar, listOf("day", "hour", "microsecond", "millisecond", "minute", "month", "monthCode", "nanosecond", "second", "year"))

            // 7. Let partialDateTime be ? PrepareTemporalFields(temporalDateTimeLike, fieldNames, partial).
            val partialDateTime = TemporalAOs.prepareTemporalFields(temporalDateTimeLike, fieldNames, null)

            // 8. Set options to ? GetOptionsObject(options).
            val options = TemporalAOs.getOptionsObject(arguments.argument(1))

            // 9. Let fields be ? PrepareTemporalFields(dateTime, fieldNames, «»).
            var fields = TemporalAOs.prepareTemporalFields(dateTime, fieldNames, emptySet())

            // 10. Set fields to ? CalendarMergeFields(calendar, fields, partialDateTime).
            fields = TemporalAOs.calendarMergeFields(calendar, fields, partialDateTime)

            // 11. Set fields to ? PrepareTemporalFields(fields, fieldNames, «»).
            fields = TemporalAOs.prepareTemporalFields(fields, fieldNames, emptySet())

            // 12. Let result be ? InterpretTemporalDateTimeFields(calendar, fields, options).
            val result = TemporalAOs.interpretTemporalDateTimeFields(calendar, fields, options)

            // 13. Assert: IsValidISODate(result.[[Year]], result.[[Month]], result.[[Day]]) is true.
            ecmaAssert(TemporalAOs.isValidISODate(result.year, result.month, result.day))

            // 14. Assert: IsValidTime(result.[[Hour]], result.[[Minute]], result.[[Second]], result.[[Millisecond]], result.[[Microsecond]], result.[[Nanosecond]]) is true.
            ecmaAssert(TemporalAOs.isValidTime(result.hour, result.minute, result.second, result.millisecond, result.microsecond, result.nanosecond))

            // 15. Return ? CreateTemporalDateTime(result.[[Year]], result.[[Month]], result.[[Day]], result.[[Hour]], result.[[Minute]], result.[[Second]], result.[[Millisecond]], result.[[Microsecond]], result.[[Nanosecond]], calendar).
            return TemporalAOs.createTemporalDateTime(result.year, result.month, result.day, result.hour, result.minute, result.second, result.millisecond, result.microsecond, result.nanosecond, calendar)
        }

        @JvmStatic
        @ECMAImpl("5.3.23")
        fun withPlainTime(arguments: JSArguments): JSValue {
            val plainTimeLike = arguments.argument(0)

            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "withPlainTime")

            // 3. If plainTimeLike is undefined, then
            if (plainTimeLike == JSUndefined) {
                // a. Return ? CreateTemporalDateTime(dateTime.[[ISOYear]], dateTime.[[ISOMonth]], dateTime.[[ISODay]], 0, 0, 0, 0, 0, 0, dateTime.[[Calendar]]).
                return TemporalAOs.createTemporalDateTime(dateTime[Slot.ISOYear], dateTime[Slot.ISOMonth], dateTime[Slot.ISODay], 0, 0, 0, 0, 0, BigInteger.ZERO, dateTime[Slot.Calendar])
            }

            // 4. Let plainTime be ? ToTemporalTime(plainTimeLike).
            val plainTime = TemporalAOs.toTemporalTime(plainTimeLike)

            // 5. Return ? CreateTemporalDateTime(dateTime.[[ISOYear]], dateTime.[[ISOMonth]], dateTime.[[ISODay]], plainTime.[[ISOHour]], plainTime.[[ISOMinute]], plainTime.[[ISOSecond]], plainTime.[[ISOMillisecond]], plainTime.[[ISOMicrosecond]], plainTime.[[ISONanosecond]], dateTime.[[Calendar]]).
            return TemporalAOs.createTemporalDateTime(
                dateTime[Slot.ISOYear], 
                dateTime[Slot.ISOMonth], 
                dateTime[Slot.ISODay],
                plainTime[Slot.ISOHour],
                plainTime[Slot.ISOMinute],
                plainTime[Slot.ISOSecond],
                plainTime[Slot.ISOMillisecond],
                plainTime[Slot.ISOMicrosecond],
                plainTime[Slot.ISONanosecond],
                dateTime[Slot.Calendar],
            )
        }

        @JvmStatic
        @ECMAImpl("5.3.24")
        fun withPlainDate(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "withPlainDate")

            // 3. Let plainDate be ? ToTemporalDate(plainDateLike).
            val plainDate = TemporalAOs.toTemporalDate(arguments.argument(0))

            // 4. Let calendar be ? ConsolidateCalendars(dateTime.[[Calendar]], plainDate.[[Calendar]]).
            val calendar = TemporalAOs.consolidateCalendars(dateTime[Slot.Calendar], plainDate[Slot.Calendar])

            // 5. Return ? CreateTemporalDateTime(plainDate.[[ISOYear]], plainDate.[[ISOMonth]], plainDate.[[ISODay]], dateTime.[[ISOHour]], dateTime.[[ISOMinute]], dateTime.[[ISOSecond]], dateTime.[[ISOMillisecond]], dateTime.[[ISOMicrosecond]], dateTime.[[ISONanosecond]], calendar).
            return TemporalAOs.createTemporalDateTime(
                plainDate[Slot.ISOYear],
                plainDate[Slot.ISOMonth],
                plainDate[Slot.ISODay],
                dateTime[Slot.ISOHour],
                dateTime[Slot.ISOMinute],
                dateTime[Slot.ISOSecond],
                dateTime[Slot.ISOMillisecond],
                dateTime[Slot.ISOMicrosecond],
                dateTime[Slot.ISONanosecond],
                calendar,
            )
        }

        @JvmStatic
        @ECMAImpl("5.3.25")
        fun withCalendar(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "withCalendar")

            // 3. Let calendar be ? ToTemporalCalendar(calendarLike).
            val calendar = TemporalAOs.toTemporalCalendar(arguments.argument(0))

            // 4. Return ? CreateTemporalDateTime(dateTime.[[ISOYear]], dateTime.[[ISOMonth]], dateTime.[[ISODay]], dateTime.[[ISOHour]], dateTime.[[ISOMinute]], dateTime.[[ISOSecond]], dateTime.[[ISOMillisecond]], dateTime.[[ISOMicrosecond]], dateTime.[[ISONanosecond]], calendar).
            return TemporalAOs.createTemporalDateTime(
                dateTime[Slot.ISOYear],
                dateTime[Slot.ISOMonth],
                dateTime[Slot.ISODay],
                dateTime[Slot.ISOHour],
                dateTime[Slot.ISOMinute],
                dateTime[Slot.ISOSecond],
                dateTime[Slot.ISOMillisecond],
                dateTime[Slot.ISOMicrosecond],
                dateTime[Slot.ISONanosecond],
                calendar,
            )
        }

        @JvmStatic
        @ECMAImpl("5.3.26")
        fun add(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "add")

            // 3. Return ? AddDurationToOrSubtractDurationFromPlainDateTime(add, dateTime, temporalDurationLike, options).
            return TemporalAOs.addDurationToOrSubtractDurationFromPlainDateTime(true, dateTime, arguments.argument(0), arguments.argument(1))
        }

        @JvmStatic
        @ECMAImpl("5.3.27")
        fun subtract(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "add")

            // 3. Return ? AddDurationToOrSubtractDurationFromPlainDateTime(subtract, dateTime, temporalDurationLike, options).
            return TemporalAOs.addDurationToOrSubtractDurationFromPlainDateTime(false, dateTime, arguments.argument(0), arguments.argument(1))
        }

        @JvmStatic
        @ECMAImpl("5.3.28")
        fun until(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "until")

            // 3. Return ? DifferenceTemporalPlainDateTime(until, dateTime, other, options).
            return TemporalAOs.differenceTemporalPlainDateTime(true, dateTime, arguments.argument(0), arguments.argument(1))
        }

        @JvmStatic
        @ECMAImpl("5.3.29")
        fun since(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "since")

            // 3. Return ? DifferenceTemporalPlainDateTime(since, dateTime, other, options).
            return TemporalAOs.differenceTemporalPlainDateTime(false, dateTime, arguments.argument(0), arguments.argument(1))
        }

        @JvmStatic
        @ECMAImpl("5.3.30")
        fun round(arguments: JSArguments): JSValue {
            var roundTo = arguments.argument(0)

            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "round")

            // 3. If roundTo is undefined, then
            if (roundTo == JSUndefined) {
                // a. Throw a TypeError exception.
                Errors.TODO("PlainDateTime.prototype.round").throwTypeError()
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

            // 9. Let result be ! RoundISODateTime(dateTime.[[ISOYear]], dateTime.[[ISOMonth]], dateTime.[[ISODay]], dateTime.[[ISOHour]], dateTime.[[ISOMinute]], dateTime.[[ISOSecond]], dateTime.[[ISOMillisecond]], dateTime.[[ISOMicrosecond]], dateTime.[[ISONanosecond]], roundingIncrement, smallestUnit, roundingMode).
            val result = TemporalAOs.roundISODateTime(
                dateTime[Slot.ISOYear],
                dateTime[Slot.ISOMonth],
                dateTime[Slot.ISODay],
                dateTime[Slot.ISOHour],
                dateTime[Slot.ISOMinute],
                dateTime[Slot.ISOSecond],
                dateTime[Slot.ISOMillisecond],
                dateTime[Slot.ISOMicrosecond],
                dateTime[Slot.ISONanosecond],
                roundingIncrement,
                smallestUnit,
                roundingMode,
            )

            // 10. Return ? CreateTemporalDateTime(result.[[Year]], result.[[Month]], result.[[Day]], result.[[Hour]], result.[[Minute]], result.[[Second]], result.[[Millisecond]], result.[[Microsecond]], result.[[Nanosecond]], dateTime.[[Calendar]]).
            return TemporalAOs.createTemporalDateTime(
                result.year,
                result.month,
                result.day,
                result.hour,
                result.minute,
                result.second,
                result.millisecond,
                result.microsecond,
                result.nanosecond,
                dateTime[Slot.Calendar],
            )
        }

        @JvmStatic
        @ECMAImpl("5.3.31")
        fun equals(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "equals")

            // 3. Set other to ? ToTemporalDateTime(other).
            val other = TemporalAOs.toTemporalDateTime(arguments.argument(0))

            // 4. Let result be ! CompareISODateTime(dateTime.[[ISOYear]], dateTime.[[ISOMonth]], dateTime.[[ISODay]], dateTime.[[ISOHour]], dateTime.[[ISOMinute]], dateTime.[[ISOSecond]], dateTime.[[ISOMillisecond]], dateTime.[[ISOMicrosecond]], dateTime.[[ISONanosecond]], other.[[ISOYear]], other.[[ISOMonth]], other.[[ISODay]], other.[[ISOHour]], other.[[ISOMinute]], other.[[ISOSecond]], other.[[ISOMillisecond]], other.[[ISOMicrosecond]], other.[[ISONanosecond]]).
            val result = TemporalAOs.compareISODateTime(
                dateTime[Slot.ISOYear],
                dateTime[Slot.ISOMonth],
                dateTime[Slot.ISODay],
                dateTime[Slot.ISOHour],
                dateTime[Slot.ISOMinute],
                dateTime[Slot.ISOSecond],
                dateTime[Slot.ISOMillisecond],
                dateTime[Slot.ISOMicrosecond],
                dateTime[Slot.ISONanosecond],
                other[Slot.ISOYear],
                other[Slot.ISOMonth],
                other[Slot.ISODay],
                other[Slot.ISOHour],
                other[Slot.ISOMinute],
                other[Slot.ISOSecond],
                other[Slot.ISOMillisecond],
                other[Slot.ISOMicrosecond],
                other[Slot.ISONanosecond],
            )

            // 5. If result is not 0, return false.
            if (result != 0)
                return JSFalse

            // 6. Return ? CalendarEquals(dateTime.[[Calendar]], other.[[Calendar]]).
            return TemporalAOs.calendarEquals(dateTime[Slot.Calendar], other[Slot.Calendar]).toValue()
        }

        @JvmStatic
        @ECMAImpl("5.3.32")
        fun toString(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "toString")

            // 3. Set options to ? GetOptionsObject(options).
            val options = TemporalAOs.getOptionsObject(arguments.argument(0))

            // 4. Let precision be ? ToSecondsStringPrecision(options).
            val precision = TemporalAOs.toSecondsStringPrecision(options)

            // 5. Let roundingMode be ? ToTemporalRoundingMode(options, "trunc").
            val roundingMode = TemporalAOs.toTemporalRoundingMode(options, "trunc")

            // 6. Let showCalendar be ? ToShowCalendarOption(options).
            val showCalendar = TemporalAOs.toShowCalendarOption(options)

            // 7. Let result be ! RoundISODateTime(dateTime.[[ISOYear]], dateTime.[[ISOMonth]], dateTime.[[ISODay]], dateTime.[[ISOHour]], dateTime.[[ISOMinute]], dateTime.[[ISOSecond]], dateTime.[[ISOMillisecond]], dateTime.[[ISOMicrosecond]], dateTime.[[ISONanosecond]], precision.[[Increment]], precision.[[Unit]], roundingMode).
            val result = TemporalAOs.roundISODateTime(
                dateTime[Slot.ISOYear],
                dateTime[Slot.ISOMonth],
                dateTime[Slot.ISODay],
                dateTime[Slot.ISOHour],
                dateTime[Slot.ISOMinute],
                dateTime[Slot.ISOSecond],
                dateTime[Slot.ISOMillisecond],
                dateTime[Slot.ISOMicrosecond],
                dateTime[Slot.ISONanosecond],
                precision.increment,
                precision.unit,
                roundingMode,
            )

            // 8. Return ? TemporalDateTimeToString(result.[[Year]], result.[[Month]], result.[[Day]], result.[[Hour]], result.[[Minute]], result.[[Second]], result.[[Millisecond]], result.[[Microsecond]], result.[[Nanosecond]], dateTime.[[Calendar]], precision.[[Precision]], showCalendar).
            return TemporalAOs.temporalDateTimeToString(
                result.year,
                result.month,
                result.day,
                result.hour,
                result.minute,
                result.second,
                result.millisecond,
                result.microsecond,
                result.nanosecond,
                dateTime[Slot.Calendar],
                precision.precision as String,
                showCalendar,
            ).toValue()
        }

        @JvmStatic
        @ECMAImpl("5.3.33")
        fun toLocaleString(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "toLocaleString")

            // 3. Return ? TemporalDateTimeToString(dateTime.[[ISOYear]], dateTime.[[ISOMonth]], dateTime.[[ISODay]], dateTime.[[ISOHour]], dateTime.[[ISOMinute]], dateTime.[[ISOSecond]], dateTime.[[ISOMillisecond]], dateTime.[[ISOMicrosecond]], dateTime.[[ISONanosecond]], dateTime.[[Calendar]], "auto", "auto").
            return TemporalAOs.temporalDateTimeToString(
                dateTime[Slot.ISOYear],
                dateTime[Slot.ISOMonth],
                dateTime[Slot.ISODay],
                dateTime[Slot.ISOHour],
                dateTime[Slot.ISOMinute],
                dateTime[Slot.ISOSecond],
                dateTime[Slot.ISOMillisecond],
                dateTime[Slot.ISOMicrosecond],
                dateTime[Slot.ISONanosecond],
                dateTime[Slot.Calendar],
                "auto",
                "auto",
            ).toValue()
        }

        @JvmStatic
        @ECMAImpl("5.3.34")
        fun toJSON(arguments: JSArguments): JSValue {
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "toJSON")

            // 3. Return ? TemporalDateTimeToString(dateTime.[[ISOYear]], dateTime.[[ISOMonth]], dateTime.[[ISODay]], dateTime.[[ISOHour]], dateTime.[[ISOMinute]], dateTime.[[ISOSecond]], dateTime.[[ISOMillisecond]], dateTime.[[ISOMicrosecond]], dateTime.[[ISONanosecond]], dateTime.[[Calendar]], "auto", "auto").
            return TemporalAOs.temporalDateTimeToString(
                dateTime[Slot.ISOYear],
                dateTime[Slot.ISOMonth],
                dateTime[Slot.ISODay],
                dateTime[Slot.ISOHour],
                dateTime[Slot.ISOMinute],
                dateTime[Slot.ISOSecond],
                dateTime[Slot.ISOMillisecond],
                dateTime[Slot.ISOMicrosecond],
                dateTime[Slot.ISONanosecond],
                dateTime[Slot.Calendar],
                "auto",
                "auto",
            ).toValue()
        }

        @JvmStatic
        @ECMAImpl("5.3.35")
        fun valueOf(arguments: JSArguments): JSValue {
            // 1. Throw a TypeError exception.
            Errors.TODO("PlainDateTime.prototype.valueOf").throwTypeError()
        }

        @JvmStatic
        @ECMAImpl("5.3.36")
        fun toZonedDateTime(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "toZonedDateTime")

            // 3. Let timeZone be ? ToTemporalTimeZone(temporalTimeZoneLike).
            val timeZone = TemporalAOs.toTemporalTimeZone(arguments.argument(0))

            // 4. Set options to ? GetOptionsObject(options).
            val options = TemporalAOs.getOptionsObject(arguments.argument(1))

            // 5. Let disambiguation be ? ToTemporalDisambiguation(options).
            val disambiguation = TemporalAOs.toTemporalDisambiguation(options)

            // 6. Let instant be ? BuiltinTimeZoneGetInstantFor(timeZone, dateTime, disambiguation).
            val instant = TemporalAOs.builtinTimeZoneGetInstantFor(timeZone, dateTime, disambiguation)

            // 7. Return ! CreateTemporalZonedDateTime(instant.[[Nanoseconds]], timeZone, dateTime.[[Calendar]]).
            return TemporalAOs.createTemporalZonedDateTime(instant[Slot.Nanoseconds], timeZone, dateTime[Slot.Calendar])
        }

        @JvmStatic
        @ECMAImpl("5.3.37")
        fun toPlainDate(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "toPlainDate")

            // 3. Return ! CreateTemporalDate(dateTime.[[ISOYear]], dateTime.[[ISOMonth]], dateTime.[[ISODay]], dateTime.[[Calendar]]).
            return TemporalAOs.createTemporalDate(dateTime[Slot.ISOYear], dateTime[Slot.ISOMonth], dateTime[Slot.ISODay], dateTime[Slot.Calendar])
        }

        @JvmStatic
        @ECMAImpl("5.3.38")
        fun toPlainYearMonth(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "toPlainYearMonth")

            // 3. Let calendar be dateTime.[[Calendar]].
            val calendar = dateTime[Slot.Calendar]

            // 4. Let fieldNames be ? CalendarFields(calendar, « "monthCode", "year" »).
            val fieldNames = TemporalAOs.calendarFields(calendar, listOf("monthCode", "year"))

            // 5. Let fields be ? PrepareTemporalFields(dateTime, fieldNames, «»).
            val fields = TemporalAOs.prepareTemporalFields(dateTime, fieldNames, emptySet())

            // 6. Return ? CalendarYearMonthFromFields(calendar, fields).
            return TemporalAOs.calendarYearMonthFromFields(calendar, fields)
        }

        @JvmStatic
        @ECMAImpl("5.3.39")
        fun toPlainMonthDay(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "toPlainMonthDay")

            // 3. Let calendar be dateTime.[[Calendar]].
            val calendar = dateTime[Slot.Calendar]

            // 4. Let fieldNames be ? CalendarFields(calendar, « "day", "monthCode" »).
            val fieldNames = TemporalAOs.calendarFields(calendar, listOf("day", "monthCode"))

            // 5. Let fields be ? PrepareTemporalFields(dateTime, fieldNames, «»).
            val fields = TemporalAOs.prepareTemporalFields(dateTime, fieldNames, emptySet())

            // 6. Return ? CalendarMonthDayFromFields(calendar, fields).
            return TemporalAOs.calendarMonthDayFromFields(calendar, fields)
        }

        @JvmStatic
        @ECMAImpl("5.3.40")
        fun toPlainTime(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "toPlainTime")

            // 3. Return ! CreateTemporalTime(dateTime.[[ISOHour]], dateTime.[[ISOMinute]], dateTime.[[ISOSecond]], dateTime.[[ISOMillisecond]], dateTime.[[ISOMicrosecond]], dateTime.[[ISONanosecond]]).
            return TemporalAOs.createTemporalTime(
                dateTime[Slot.ISOHour],
                dateTime[Slot.ISOMinute],
                dateTime[Slot.ISOSecond],
                dateTime[Slot.ISOMillisecond],
                dateTime[Slot.ISOMicrosecond],
                dateTime[Slot.ISONanosecond],
            )
        }

        @JvmStatic
        @ECMAImpl("5.3.41")
        fun getISOFields(arguments: JSArguments): JSValue {
            // 1. Let dateTime be the this value.
            // 2. Perform ? RequireInternalSlot(dateTime, [[InitializedTemporalDateTime]]).
            val dateTime = thisPlainDateTime(arguments.thisValue, "getISOFields")

            // 3. Let fields be OrdinaryObjectCreate(%Object.prototype%).
            val fields = JSObject.create()

            // 4. Perform ! CreateDataPropertyOrThrow(fields, "calendar", dateTime.[[Calendar]]).
            AOs.createDataPropertyOrThrow(fields, "calendar".key(), dateTime[Slot.Calendar])

            // 5. Perform ! CreateDataPropertyOrThrow(fields, "isoDay", 𝔽(dateTime.[[ISODay]])).
            AOs.createDataPropertyOrThrow(fields, "isoDay".key(), dateTime[Slot.ISODay].toValue())

            // 6. Perform ! CreateDataPropertyOrThrow(fields, "isoHour", 𝔽(dateTime.[[ISOHour]])).
            AOs.createDataPropertyOrThrow(fields, "isoHour".key(), dateTime[Slot.ISOHour].toValue())

            // 7. Perform ! CreateDataPropertyOrThrow(fields, "isoMicrosecond", 𝔽(dateTime.[[ISOMicrosecond]])).
            AOs.createDataPropertyOrThrow(fields, "isoMicrosecond".key(), dateTime[Slot.ISOMicrosecond].toValue())

            // 8. Perform ! CreateDataPropertyOrThrow(fields, "isoMillisecond", 𝔽(dateTime.[[ISOMillisecond]])).
            AOs.createDataPropertyOrThrow(fields, "isoMillisecond".key(), dateTime[Slot.ISOMillisecond].toValue())

            // 9. Perform ! CreateDataPropertyOrThrow(fields, "isoMinute", 𝔽(dateTime.[[ISOMinute]])).
            AOs.createDataPropertyOrThrow(fields, "isoMinute".key(), dateTime[Slot.ISOMinute].toValue())

            // 10. Perform ! CreateDataPropertyOrThrow(fields, "isoMonth", 𝔽(dateTime.[[ISOMonth]])).
            AOs.createDataPropertyOrThrow(fields, "isoMonth".key(), dateTime[Slot.ISOMonth].toValue())

            // 11. Perform ! CreateDataPropertyOrThrow(fields, "isoNanosecond", 𝔽(dateTime.[[ISONanosecond]])).
            AOs.createDataPropertyOrThrow(fields, "isoNanosecond".key(), dateTime[Slot.ISONanosecond].toValue())

            // 12. Perform ! CreateDataPropertyOrThrow(fields, "isoSecond", 𝔽(dateTime.[[ISOSecond]])).
            AOs.createDataPropertyOrThrow(fields, "isoSecond".key(), dateTime[Slot.ISOSecond].toValue())

            // 13. Perform ! CreateDataPropertyOrThrow(fields, "isoYear", 𝔽(dateTime.[[ISOYear]])).
            AOs.createDataPropertyOrThrow(fields, "isoYear".key(), dateTime[Slot.ISOYear].toValue())

            // 14. Return fields.
            return fields
        }
    }
}