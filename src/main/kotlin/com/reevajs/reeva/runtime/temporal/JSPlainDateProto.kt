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
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.temporal.TemporalAOs
import com.reevajs.reeva.utils.*
import java.math.BigInteger

class JSPlainDateProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("constructor", realm.plainDateCtor)
        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "Temporal.PlainDate".toValue(), attrs { +conf })

        defineBuiltinGetter("calendar", ::getCalendar)
        defineBuiltinGetter("year", ::getYear)
        defineBuiltinGetter("month", ::getMonth)
        defineBuiltinGetter("monthCode", ::getMonthCode)
        defineBuiltinGetter("day", ::getDay)
        defineBuiltinGetter("dayOfWeek", ::getDayOfWeek)
        defineBuiltinGetter("dayOfYear", ::getDayOfYear)
        defineBuiltinGetter("weekOfYear", ::getWeekOfYear)
        defineBuiltinGetter("daysInWeek", ::getDaysInWeek)
        defineBuiltinGetter("daysInMonth", ::getDaysInMonth)
        defineBuiltinGetter("daysInYear", ::getDaysInYear)
        defineBuiltinGetter("monthsInYear", ::getMonthsInYear)
        defineBuiltinGetter("inLeapYear", ::getInLeapYear)

        defineBuiltin("toPlainYearMonth", 0, ::toPlainYearMonth)
        defineBuiltin("toPlainMonthDay", 0, ::toPlainMonthDay)
        defineBuiltin("getISOFields", 0, ::getISOFields)
        defineBuiltin("add", 1, ::add)
        defineBuiltin("subtract", 1, ::subtract)
        defineBuiltin("with", 1, ::with)
        defineBuiltin("withCalendar", 1, ::withCalendar)
        defineBuiltin("until", 1, ::until)
        defineBuiltin("since", 1, ::since)
        defineBuiltin("equals", 1, ::equals)
        defineBuiltin("toPlainDateTime", 0, ::toPlainDateTime)
        defineBuiltin("toZonedDateTime", 1, ::toZonedDateTime)
        defineBuiltin("toString", 0, ::toString)
        defineBuiltin("toLocaleString", 0, ::toLocaleString)
        defineBuiltin("toJSON", 0, ::toJSON)
        defineBuiltin("valueOf", 0, ::valueOf)
    }

    companion object {
        @JvmStatic
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSPlainDateProto(realm).initialize()

        private fun thisPlainDate(thisValue: JSValue, method: String): JSObject {
            if (!AOs.requireInternalSlot(thisValue, Slot.InitializedTemporalDate))
                Errors.IncompatibleMethodCall("PlainDate.prototype.$method").throwTypeError()
            return thisValue
        }

        private fun basicGetterHelper(
            arguments: JSArguments, 
            methodName: String, 
            method: (JSObject, JSValue) -> JSValue,
        ): JSValue {
            val temporalDate = thisPlainDate(arguments.thisValue, methodName)
            val calendar = temporalDate[Slot.Calendar]
            return method(calendar, temporalDate)
        }

        @JvmStatic
        @ECMAImpl("3.3.3")
        fun getCalendar(arguments: JSArguments): JSValue {
            // 1. Let temporalDate be the this value.
            // 2. Perform ? RequireInternalSlot(temporalDate, [[InitializedTemporalDate]]).
            // 3. Return temporalDate.[[Calendar]].
            return thisPlainDate(arguments.thisValue, "get calendar")[Slot.Calendar].toValue()
        }

        @JvmStatic
        @ECMAImpl("3.3.4")
        fun getYear(arguments: JSArguments): JSValue {
            // 1. Let temporalDate be the this value.
            // 2. Perform ? RequireInternalSlot(temporalDate, [[InitializedTemporalDate]]).
            // 3. Let calendar be temporalDate.[[Calendar]].
            // 4. Return ? CalendarYear(calendar, temporalDate).
            return basicGetterHelper(arguments, "get year", TemporalAOs::calendarYear)
        }
        
        @JvmStatic
        @ECMAImpl("3.3.5")
        fun getMonth(arguments: JSArguments): JSValue {
            // 1. Let temporalDate be the this value.
            // 2. Perform ? RequireInternalSlot(temporalDate, [[InitializedTemporalDate]]).
            // 3. Let calendar be temporalDate.[[Calendar]].
            // 4. Return ? CalendarMonth(calendar, temporalDate).
            return basicGetterHelper(arguments, "get month", TemporalAOs::calendarMonth)
        }

        @JvmStatic
        @ECMAImpl("3.3.6")
        fun getMonthCode(arguments: JSArguments): JSValue {
            // 1. Let temporalDate be the this value.
            // 2. Perform ? RequireInternalSlot(temporalDate, [[InitializedTemporalDate]]).
            // 3. Let calendar be temporalDate.[[Calendar]].
            // 4. Return ? CalendarMonthCode(calendar, temporalDate).
            return basicGetterHelper(arguments, "get monthCode", TemporalAOs::calendarMonthCode)
        }

        @JvmStatic
        @ECMAImpl("3.3.7")
        fun getDay(arguments: JSArguments): JSValue {
            // 1. Let temporalDate be the this value.
            // 2. Perform ? RequireInternalSlot(temporalDate, [[InitializedTemporalDate]]).
            // 3. Let calendar be temporalDate.[[Calendar]].
            // 4. Return ? CalendarDay(calendar, temporalDate).
            return basicGetterHelper(arguments, "get day", TemporalAOs::calendarDay)
        }

        @JvmStatic
        @ECMAImpl("3.3.8")
        fun getDayOfWeek(arguments: JSArguments): JSValue {
            // 1. Let temporalDate be the this value.
            // 2. Perform ? RequireInternalSlot(temporalDate, [[InitializedTemporalDate]]).
            // 3. Let calendar be temporalDate.[[Calendar]].
            // 4. Return ? CalendarDayOfWeek(calendar, temporalDate).
            return basicGetterHelper(arguments, "get dayOfWeek", TemporalAOs::calendarDayOfWeek)
        }

        @JvmStatic
        @ECMAImpl("3.3.9")
        fun getDayOfYear(arguments: JSArguments): JSValue {
            // 1. Let temporalDate be the this value.
            // 2. Perform ? RequireInternalSlot(temporalDate, [[InitializedTemporalDate]]).
            // 3. Let calendar be temporalDate.[[Calendar]].
            // 4. Return ? CalendarDayOfYear(calendar, temporalDate).
            return basicGetterHelper(arguments, "get dayOfYear", TemporalAOs::calendarDayOfYear)
        }

        @JvmStatic
        @ECMAImpl("3.3.10")
        fun getWeekOfYear(arguments: JSArguments): JSValue {
            // 1. Let temporalDate be the this value.
            // 2. Perform ? RequireInternalSlot(temporalDate, [[InitializedTemporalDate]]).
            // 3. Let calendar be temporalDate.[[Calendar]].
            // 4. Return ? CalendarWeekOfYear(calendar, temporalDate).
            return basicGetterHelper(arguments, "get weekOfYear", TemporalAOs::calendarWeekOfYear)
        }

        @JvmStatic
        @ECMAImpl("3.3.11")
        fun getDaysInWeek(arguments: JSArguments): JSValue {
            // 1. Let temporalDate be the this value.
            // 2. Perform ? RequireInternalSlot(temporalDate, [[InitializedTemporalDate]]).
            // 3. Let calendar be temporalDate.[[Calendar]].
            // 4. Return ? CalendarDaysInWeek(calendar, temporalDate).
            return basicGetterHelper(arguments, "get daysInWeek", TemporalAOs::calendarDaysInWeek)
        }

        @JvmStatic
        @ECMAImpl("3.3.12")
        fun getDaysInMonth(arguments: JSArguments): JSValue {
            // 1. Let temporalDate be the this value.
            // 2. Perform ? RequireInternalSlot(temporalDate, [[InitializedTemporalDate]]).
            // 3. Let calendar be temporalDate.[[Calendar]].
            // 4. Return ? CalendarDaysInMonth(calendar, temporalDate).
            return basicGetterHelper(arguments, "get daysInMonth", TemporalAOs::calendarDaysInMonth)
        }

        @JvmStatic
        @ECMAImpl("3.3.13")
        fun getDaysInYear(arguments: JSArguments): JSValue {
            // 1. Let temporalDate be the this value.
            // 2. Perform ? RequireInternalSlot(temporalDate, [[InitializedTemporalDate]]).
            // 3. Let calendar be temporalDate.[[Calendar]].
            // 4. Return ? CalendarDaysInYear(calendar, temporalDate).
            return basicGetterHelper(arguments, "get daysInYear", TemporalAOs::calendarDaysInYear)
        }

        @JvmStatic
        @ECMAImpl("3.3.14")
        fun getMonthsInYear(arguments: JSArguments): JSValue {
            // 1. Let temporalDate be the this value.
            // 2. Perform ? RequireInternalSlot(temporalDate, [[InitializedTemporalDate]]).
            // 3. Let calendar be temporalDate.[[Calendar]].
            // 4. Return ? CalendarMonthsInYear(calendar, temporalDate).
            return basicGetterHelper(arguments, "get monthsInYear", TemporalAOs::calendarMonthsInYear)
        }

        @JvmStatic
        @ECMAImpl("3.3.15")
        fun getInLeapYear(arguments: JSArguments): JSValue {
            // 1. Let temporalDate be the this value.
            // 2. Perform ? RequireInternalSlot(temporalDate, [[InitializedTemporalDate]]).
            // 3. Let calendar be temporalDate.[[Calendar]].
            // 4. Return ? CalendarInLeapYear(calendar, temporalDate).
            return basicGetterHelper(arguments, "get inLeapYear", TemporalAOs::calendarInLeapYear)
        }

        @JvmStatic
        @ECMAImpl("3.3.16")
        fun toPlainYearMonth(arguments: JSArguments): JSValue {
            // 1. Let temporalDate be the this value.
            // 2. Perform ? RequireInternalSlot(temporalDate, [[InitializedTemporalDate]]).
            val temporalDate = thisPlainDate(arguments.thisValue, "toPlainYearMonth")

            // 3. Let calendar be temporalDate.[[Calendar]].
            val calendar = temporalDate[Slot.Calendar]

            // 4. Let fieldNames be ? CalendarFields(calendar, « "monthCode", "year" »).
            val fieldNames = TemporalAOs.calendarFields(calendar, listOf("monthCode", "year"))

            // 5. Let fields be ? PrepareTemporalFields(temporalDate, fieldNames, «»).
            val fields = TemporalAOs.prepareTemporalFields(temporalDate, fieldNames, emptySet())
            
            // 6. Return ? CalendarYearMonthFromFields(calendar, fields).
            return TemporalAOs.calendarYearMonthFromFields(calendar, fields)
        }

        @JvmStatic
        @ECMAImpl("3.3.17")
        fun toPlainMonthDay(arguments: JSArguments): JSValue {
            // 1. Let temporalDate be the this value.
            // 2. Perform ? RequireInternalSlot(temporalDate, [[InitializedTemporalDate]]).
            val temporalDate = thisPlainDate(arguments.thisValue, "toPlainMonthDay")

            // 3. Let calendar be temporalDate.[[Calendar]].
            val calendar = temporalDate[Slot.Calendar]

            // 4. Let fieldNames be ? CalendarFields(calendar, « "monthCode", "year" »).
            val fieldNames = TemporalAOs.calendarFields(calendar, listOf("day", "monthCode"))

            // 5. Let fields be ? PrepareTemporalFields(temporalDate, fieldNames, «»).
            val fields = TemporalAOs.prepareTemporalFields(temporalDate, fieldNames, emptySet())
            
            // 6. Return ? CalendarMonthDayFromFields(calendar, fields).
            return TemporalAOs.calendarMonthDayFromFields(calendar, fields)
        }

        @JvmStatic
        @ECMAImpl("3.3.18")
        fun getISOFields(arguments: JSArguments): JSValue {
            // 1. Let temporalDate be the this value.
            // 2. Perform ? RequireInternalSlot(temporalDate, [[InitializedTemporalDate]]).
            val temporalDate = thisPlainDate(arguments.thisValue, "getISOFields")

            // 3. Let fields be OrdinaryObjectCreate(%Object.prototype%).
            val fields = JSObject.create()

            // 4. Perform ! CreateDataPropertyOrThrow(fields, "calendar", temporalDate.[[Calendar]]).
            AOs.createDataPropertyOrThrow(fields, "calendar".key(), temporalDate[Slot.Calendar])
            
            // 5. Perform ! CreateDataPropertyOrThrow(fields, "isoDay", 𝔽(temporalDate.[[ISODay]])).
            AOs.createDataPropertyOrThrow(fields, "isoDay".key(), temporalDate[Slot.ISODay].toValue())
            
            // 6. Perform ! CreateDataPropertyOrThrow(fields, "isoMonth", 𝔽(temporalDate.[[ISOMonth]])).
            AOs.createDataPropertyOrThrow(fields, "isoMonth".key(), temporalDate[Slot.ISOMonth].toValue())
            
            // 7. Perform ! CreateDataPropertyOrThrow(fields, "isoYear", 𝔽(temporalDate.[[ISOYear]])).
            AOs.createDataPropertyOrThrow(fields, "isoYear".key(), temporalDate[Slot.ISOYear].toValue())

            // 8. Return fields.
            return fields
        }

        @JvmStatic
        @ECMAImpl("3.3.19")
        fun add(arguments: JSArguments): JSValue {
            // 1. Let temporalDate be the this value.
            // 2. Perform ? RequireInternalSlot(temporalDate, [[InitializedTemporalDate]]).
            val temporalDate = thisPlainDate(arguments.thisValue, "add")

            // 3. Let duration be ? ToTemporalDuration(temporalDurationLike).
            val duration = TemporalAOs.toTemporalDuration(arguments.argument(0))

            // 4. Set options to ? GetOptionsObject(options).
            val options = TemporalAOs.getOptionsObject(arguments.argument(1))

            // 5. Return ? CalendarDateAdd(temporalDate.[[Calendar]], temporalDate, duration, options).
            return TemporalAOs.calendarDateAdd(temporalDate[Slot.Calendar], temporalDate, duration, options)
        }

        @JvmStatic
        @ECMAImpl("3.3.20")
        fun subtract(arguments: JSArguments): JSValue {
            // 1. Let temporalDate be the this value.
            // 2. Perform ? RequireInternalSlot(temporalDate, [[InitializedTemporalDate]]).
            val temporalDate = thisPlainDate(arguments.thisValue, "add")

            // 3. Let duration be ? ToTemporalDuration(temporalDurationLike).
            val duration = TemporalAOs.toTemporalDuration(arguments.argument(0))

            // 4. Set options to ? GetOptionsObject(options).
            val options = TemporalAOs.getOptionsObject(arguments.argument(1))

            // 5. Let negatedDuration be ! CreateNegatedTemporalDuration(duration).
            val negatedDuration = TemporalAOs.createNegatedTemporalDuration(duration)

            // 6. Return ? CalendarDateAdd(temporalDate.[[Calendar]], temporalDate, negatedDuration, options).
            return TemporalAOs.calendarDateAdd(temporalDate[Slot.Calendar], temporalDate, negatedDuration, options)
        }

        @JvmStatic
        @ECMAImpl("3.3.21")
        fun with(arguments: JSArguments): JSValue {
            val temporalDateLike = arguments.argument(0)

            // 1. Let temporalDate be the this value.
            // 2. Perform ? RequireInternalSlot(temporalDate, [[InitializedTemporalDate]]).
            val temporalDate = thisPlainDate(arguments.thisValue, "add")

            // 3. If Type(temporalDateLike) is not Object, then
            if (temporalDateLike !is JSObject) {
                // a. Throw a TypeError exception.
                Errors.TODO("PlainDate.prototype.with").throwTypeError()
            }

            // 4. Perform ? RejectObjectWithCalendarOrTimeZone(temporalDateLike).
            TemporalAOs.rejectObjectWithCalendarOrTimeZone(temporalDateLike)

            // 5. Let calendar be temporalDate.[[Calendar]].
            val calendar = temporalDate[Slot.Calendar]

            // 6. Let fieldNames be ? CalendarFields(calendar, « "day", "month", "monthCode", "year" »).
            val fieldNames = TemporalAOs.calendarFields(calendar, listOf("day", "month", "monthCode", "year"))

            // 7. Let partialDate be ? PrepareTemporalFields(temporalDateLike, fieldNames, partial).
            val partialDate = TemporalAOs.prepareTemporalFields(temporalDateLike, fieldNames, null)

            // 8. Set options to ? GetOptionsObject(options).
            val options = TemporalAOs.getOptionsObject(arguments.argument(1))

            // 9. Let fields be ? PrepareTemporalFields(temporalDate, fieldNames, «»).
            var fields = TemporalAOs.prepareTemporalFields(temporalDate, fieldNames, emptySet())

            // 10. Set fields to ? CalendarMergeFields(calendar, fields, partialDate).
            fields = TemporalAOs.calendarMergeFields(calendar, fields, partialDate)

            // 11. Set fields to ? PrepareTemporalFields(fields, fieldNames, «»).
            fields = TemporalAOs.prepareTemporalFields(fields, fieldNames, emptySet())

            // 12. Return ? CalendarDateFromFields(calendar, fields, options).
            return TemporalAOs.calendarDateFromFields(calendar, fields, options)
        }

        @JvmStatic
        @ECMAImpl("3.3.22")
        fun withCalendar(arguments: JSArguments): JSValue {
            // 1. Let temporalDate be the this value.
            // 2. Perform ? RequireInternalSlot(temporalDate, [[InitializedTemporalDate]]).            
            val temporalDate = thisPlainDate(arguments.thisValue, "withCalendar")

            // 3. Let calendar be ? ToTemporalCalendar(calendarLike).
            val calendar = TemporalAOs.toTemporalCalendar(arguments.argument(0))

            // 4. Return ! CreateTemporalDate(temporalDate.[[ISOYear]], temporalDate.[[ISOMonth]], temporalDate.[[ISODay]], calendar).
            return TemporalAOs.createTemporalDate(temporalDate[Slot.ISOYear], temporalDate[Slot.ISOMonth], temporalDate[Slot.ISODay], calendar)
        }

        @JvmStatic
        @ECMAImpl("3.3.23")
        fun until(arguments: JSArguments): JSValue {
            // 1. Let temporalDate be the this value.
            // 2. Perform ? RequireInternalSlot(temporalDate, [[InitializedTemporalDate]]).
            val temporalDate = thisPlainDate(arguments.thisValue, "until")

            // 3. Return ? DifferenceTemporalPlainDate(until, temporalDate, other, options).
            return TemporalAOs.differenceTemporalPlainDate(true, temporalDate, arguments.argument(0), arguments.argument(1))
        }

        @JvmStatic
        @ECMAImpl("3.3.24")
        fun since(arguments: JSArguments): JSValue {
            // 1. Let temporalDate be the this value.
            // 2. Perform ? RequireInternalSlot(temporalDate, [[InitializedTemporalDate]]).
            val temporalDate = thisPlainDate(arguments.thisValue, "since")

            // 3. Return ? DifferenceTemporalPlainDate(since, temporalDate, other, options).
            return TemporalAOs.differenceTemporalPlainDate(false, temporalDate, arguments.argument(0), arguments.argument(1))
        }

        @JvmStatic
        @ECMAImpl("3.3.25")
        fun equals(arguments: JSArguments): JSValue {
            // 1. Let temporalDate be the this value.
            // 2. Perform ? RequireInternalSlot(temporalDate, [[InitializedTemporalDate]]).
            val temporalDate = thisPlainDate(arguments.thisValue, "equals")

            // 3. Set other to ? ToTemporalDate(other).
            val other = TemporalAOs.toTemporalDate(arguments.argument(0))

            // 4. If temporalDate.[[ISOYear]] ≠ other.[[ISOYear]], return false.
            if (temporalDate[Slot.ISOYear] != other[Slot.ISOYear])
                return JSFalse

            // 5. If temporalDate.[[ISOMonth]] ≠ other.[[ISOMonth]], return false.
            if (temporalDate[Slot.ISOMonth] != other[Slot.ISOMonth])
                return JSFalse

            // 6. If temporalDate.[[ISODay]] ≠ other.[[ISODay]], return false.
            if (temporalDate[Slot.ISODay] != other[Slot.ISODay])
                return JSFalse

            // 7. Return ? CalendarEquals(temporalDate.[[Calendar]], other.[[Calendar]]).
            return TemporalAOs.calendarEquals(temporalDate[Slot.Calendar], other[Slot.Calendar]).toValue()
        }

        @JvmStatic
        @ECMAImpl("3.3.26")
        fun toPlainDateTime(arguments: JSArguments): JSValue {
            var temporalTime = arguments.argument(0)

            // 1. Let temporalDate be the this value.
            // 2. Perform ? RequireInternalSlot(temporalDate, [[InitializedTemporalDate]]).
            val temporalDate = thisPlainDate(arguments.thisValue, "toPlainDateTime")

            // 3. If temporalTime is undefined, then
            if (temporalTime == JSUndefined) {
                // a. Return ? CreateTemporalDateTime(temporalDate.[[ISOYear]], temporalDate.[[ISOMonth]], temporalDate.[[ISODay]], 0, 0, 0, 0, 0, 0, temporalDate.[[Calendar]]).
                return TemporalAOs.createTemporalDateTime(temporalDate[Slot.ISOYear], temporalDate[Slot.ISOMonth], temporalDate[Slot.ISODay], 0, 0, 0, 0, 0, BigInteger.ZERO, temporalDate[Slot.Calendar])
            }
            
            // 4. Set temporalTime to ? ToTemporalTime(temporalTime).
            temporalTime = TemporalAOs.toTemporalTime(temporalTime)
            expect(temporalTime is JSObject)
            
            // 5. Return ? CreateTemporalDateTime(temporalDate.[[ISOYear]], temporalDate.[[ISOMonth]], temporalDate.[[ISODay]], temporalTime.[[ISOHour]], temporalTime.[[ISOMinute]], temporalTime.[[ISOSecond]], temporalTime.[[ISOMillisecond]], temporalTime.[[ISOMicrosecond]], temporalTime.[[ISONanosecond]], temporalDate.[[Calendar]]).
            return TemporalAOs.createTemporalDateTime(temporalDate[Slot.ISOYear], temporalDate[Slot.ISOMonth], temporalDate[Slot.ISODay], temporalTime[Slot.ISOHour], temporalTime[Slot.ISOMinute], temporalTime[Slot.ISOSecond], temporalTime[Slot.ISOMillisecond], temporalTime[Slot.ISOMicrosecond], temporalTime[Slot.ISONanosecond], temporalDate[Slot.Calendar])
        }

        @JvmStatic
        @ECMAImpl("3.3.27")
        fun toZonedDateTime(arguments: JSArguments): JSValue {
            val item = arguments.argument(0)

            // 1. Let temporalDate be the this value.
            // 2. Perform ? RequireInternalSlot(temporalDate, [[InitializedTemporalDate]]).
            val temporalDate = thisPlainDate(arguments.thisValue, "toZonedDateTime")

            // 3. If Type(item) is Object, then
            var (timeZone, temporalTime) = if (item is JSObject) {
                // a. Let timeZoneLike be ? Get(item, "timeZone").
                val timeZoneLike = item.get("timeZone")

                // b. If timeZoneLike is undefined, then
                if (timeZoneLike == JSUndefined) {
                    // i. Let timeZone be ? ToTemporalTimeZone(item).
                    // ii. Let temporalTime be undefined.
                    TemporalAOs.toTemporalTimeZone(item) to JSUndefined
                }
                // c. Else,
                else {
                    // i. Let timeZone be ? ToTemporalTimeZone(timeZoneLike).
                    // ii. Let temporalTime be ? Get(item, "plainTime").
                    TemporalAOs.toTemporalTimeZone(timeZoneLike) to item.get("plainTime")
                }
            }
            // 4. Else,
            else {
                // a. Let timeZone be ? ToTemporalTimeZone(item).
                // b. Let temporalTime be undefined.
                TemporalAOs.toTemporalTimeZone(item) to JSUndefined
            }

            // 5. If temporalTime is undefined, then
            val temporalDateTime = if (temporalTime == JSUndefined) {
                // a. Let temporalDateTime be ? CreateTemporalDateTime(temporalDate.[[ISOYear]], temporalDate.[[ISOMonth]], temporalDate.[[ISODay]], 0, 0, 0, 0, 0, 0, temporalDate.[[Calendar]]).
                TemporalAOs.createTemporalDateTime(temporalDate[Slot.ISOYear], temporalDate[Slot.ISOMonth], temporalDate[Slot.ISODay], 0, 0, 0, 0, 0, BigInteger.ZERO, temporalDate[Slot.Calendar])
            }
            // 6. Else,
            else {
                // a. Set temporalTime to ? ToTemporalTime(temporalTime).
                temporalTime = TemporalAOs.toTemporalTime(temporalTime)
                
                // b. Let temporalDateTime be ? CreateTemporalDateTime(temporalDate.[[ISOYear]], temporalDate.[[ISOMonth]], temporalDate.[[ISODay]], temporalTime.[[ISOHour]], temporalTime.[[ISOMinute]], temporalTime.[[ISOSecond]], temporalTime.[[ISOMillisecond]], temporalTime.[[ISOMicrosecond]], temporalTime.[[ISONanosecond]], temporalDate.[[Calendar]]).
                TemporalAOs.createTemporalDateTime(temporalDate[Slot.ISOYear], temporalDate[Slot.ISOMonth], temporalDate[Slot.ISODay], temporalTime[Slot.ISOHour], temporalTime[Slot.ISOMinute], temporalTime[Slot.ISOSecond], temporalTime[Slot.ISOMillisecond], temporalTime[Slot.ISOMicrosecond], temporalTime[Slot.ISONanosecond], temporalDate[Slot.Calendar])
            }

            // 7. Let instant be ? BuiltinTimeZoneGetInstantFor(timeZone, temporalDateTime, "compatible").
            val instant = TemporalAOs.builtinTimeZoneGetInstantFor(timeZone, temporalDateTime, "compatible")

            // 8. Return ! CreateTemporalZonedDateTime(instant.[[Nanoseconds]], timeZone, temporalDate.[[Calendar]]).
            return TemporalAOs.createTemporalZonedDateTime(instant[Slot.Nanoseconds], timeZone, temporalDate[Slot.Calendar])
        }

        @JvmStatic
        @ECMAImpl("3.3.28")
        fun toString(arguments: JSArguments): JSValue {
            // 1. Let temporalDate be the this value.
            // 2. Perform ? RequireInternalSlot(temporalDate, [[InitializedTemporalDate]]).
            val temporalDate = thisPlainDate(arguments.thisValue, "toString")

            // 3. Set options to ? GetOptionsObject(options).
            val options = TemporalAOs.getOptionsObject(arguments.argument(0))

            // 4. Let showCalendar be ? ToShowCalendarOption(options).
            val showCalendar = TemporalAOs.toShowCalendarOption(options)

            // 5. Return ? TemporalDateToString(temporalDate, showCalendar).
            return TemporalAOs.temporalDateToString(temporalDate, showCalendar).toValue()
        }

        @JvmStatic
        @ECMAImpl("3.3.29")
        fun toLocaleString(arguments: JSArguments): JSValue {
            // 1. Let temporalDate be the this value.
            // 2. Perform ? RequireInternalSlot(temporalDate, [[InitializedTemporalDate]]).
            val temporalDate = thisPlainDate(arguments.thisValue, "toLocaleString")

            // 3. Return ? TemporalDateToString(temporalDate, "auto").
            return TemporalAOs.temporalDateToString(temporalDate, "auto").toValue()
        }

        @JvmStatic
        @ECMAImpl("3.3.30")
        fun toJSON(arguments: JSArguments): JSValue {
            // 1. Let temporalDate be the this value.
            // 2. Perform ? RequireInternalSlot(temporalDate, [[InitializedTemporalDate]]).
            val temporalDate = thisPlainDate(arguments.thisValue, "toJSON")

            // 3. Return ? TemporalDateToString(temporalDate, "auto").
            return TemporalAOs.temporalDateToString(temporalDate, "auto").toValue()
        }

        @JvmStatic
        @ECMAImpl("3.3.30")
        fun valueOf(arguments: JSArguments): JSValue {
            // 1. Throw a TypeError exception.
            Errors.TODO("PlainDate.prototype.valueOf").throwTypeError()
        }
    }
}
