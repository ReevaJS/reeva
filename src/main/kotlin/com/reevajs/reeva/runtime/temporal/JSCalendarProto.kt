package com.reevajs.reeva.runtime.temporal

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.errors.completion
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.toJSString
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.temporal.TemporalAOs
import com.reevajs.reeva.runtime.primitives.JSFalse
import com.reevajs.reeva.runtime.primitives.JSString
import com.reevajs.reeva.runtime.toObject
import com.reevajs.reeva.utils.*
import java.math.BigInteger

class JSCalendarProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("constructor", realm.calendarCtor)
        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "Temporal.Calendar".toValue(), attrs { +conf })

        defineBuiltinGetter("id", ::getId)

        defineBuiltin("dateFromFields", 1, ::dateFromFields)
        defineBuiltin("yearMonthFromFields", 1, ::yearMonthFromFields)
        defineBuiltin("monthDayFromFields", 1, ::monthDayFromFields)
        defineBuiltin("dateAdd", 2, ::dateAdd)
        defineBuiltin("dateUntil", 2, ::dateUntil)
        defineBuiltin("year", 1, ::year)
        defineBuiltin("month", 1, ::month)
        defineBuiltin("monthCode", 1, ::monthCode)
        defineBuiltin("day", 1, ::day)
        defineBuiltin("dayOfWeek", 1, ::dayOfWeek)
        defineBuiltin("dayOfYear", 1, ::dayOfYear)
        defineBuiltin("weekOfYear", 1, ::weekOfYear)
        defineBuiltin("daysInWeek", 1, ::daysInWeek)
        defineBuiltin("daysInMonth", 1, ::daysInMonth)
        defineBuiltin("daysInYear", 1, ::daysInYear)
        defineBuiltin("monthsInYear", 1, ::monthsInYear)
        defineBuiltin("inLeapYear", 1, ::inLeapYear)
        defineBuiltin("fields", 1, ::fields)
        defineBuiltin("mergeFields", 2, ::mergeFields)
        defineBuiltin("toString", 0, ::toString)
        defineBuiltin("toJSON", 0, ::toJSON)
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSCalendarProto(realm).initialize()

        private fun thisCalendar(thisValue: JSValue, method: String): JSObject {
            if (!AOs.requireInternalSlot(thisValue, Slot.InitializedTemporalCalendar))
                Errors.IncompatibleMethodCall("Duration.prototype.$method").throwTypeError()
            return thisValue
        }

        private fun thisCalendarId(thisValue: JSValue, method: String): String {
            return thisCalendar(thisValue, method)[Slot.Identifier]
        }

        @JvmStatic
        @ECMAImpl("12.5.3")
        fun getId(arguments: JSArguments): JSValue {
            // 1. Let calendar be the this value.
            // 2. Perform ? RequireInternalSlot(calendar, [[InitializedTemporalCalendar]]).
            // 3. Return ? ToString(calendar).
            return thisCalendarId(arguments.thisValue, "get id").toValue()
        }

        @JvmStatic
        @ECMAImpl("12.5.4")
        fun dateFromFields(arguments: JSArguments): JSValue {
            val fields = arguments.argument(0)
            var options = arguments.argument(1)

            // 1. Let calendar be the this value.
            // 2. Perform ? RequireInternalSlot(calendar, [[InitializedTemporalCalendar]]).
            val calendar = thisCalendar(arguments.thisValue, "dateFromFields")

            // 3. Assert: calendar.[[Identifier]] is "iso8601".
            ecmaAssert(calendar[Slot.Identifier] == "iso8601")

            // 4. If Type(fields) is not Object, throw a TypeError exception.
            if (fields !is JSObject)
                Errors.TODO("Calendar.prototype.dateFromFields").throwTypeError()

            // 5. Set options to ? GetOptionsObject(options).
            options = TemporalAOs.getOptionsObject(options)

            // 6. Let result be ? ISODateFromFields(fields, options).
            val result = TemporalAOs.isoDateFromFields(fields, options)

            // 7. Return ? CreateTemporalDate(result.[[Year]], result.[[Month]], result.[[Day]], calendar).
            return TemporalAOs.createTemporalDate(result.year, result.month, result.day, calendar)
        }

        @JvmStatic
        @ECMAImpl("12.5.5")
        fun yearMonthFromFields(arguments: JSArguments): JSValue {
            val fields = arguments.argument(0)
            var options = arguments.argument(1)

            // 1. Let calendar be the this value.
            // 2. Perform ? RequireInternalSlot(calendar, [[InitializedTemporalCalendar]]).
            val calendar = thisCalendar(arguments.thisValue, "yearMonthFromFields")

            // 3. Assert: calendar.[[Identifier]] is "iso8601".
            ecmaAssert(calendar[Slot.Identifier] == "iso8601")

            // 4. If Type(fields) is not Object, throw a TypeError exception.
            if (fields !is JSObject)
                Errors.TODO("Calendar.prototype.dateFromFields").throwTypeError()

            // 5. Set options to ? GetOptionsObject(options).
            options = TemporalAOs.getOptionsObject(options)

            // 6. Let result be ? ISOYearMonthFromFields(fields, options).
            val result = TemporalAOs.isoYearMonthFromFields(fields, options)

            // 7. Return ? CreateTemporalYearMonth(result.[[Year]], result.[[Month]], calendar, result.[[ReferenceISODay]]).
            return TemporalAOs.createTemporalYearMonth(result.year, result.month, calendar, result.referenceISODay)
        }

        @JvmStatic
        @ECMAImpl("12.5.6")
        fun monthDayFromFields(arguments: JSArguments): JSValue {
            val fields = arguments.argument(0)
            var options = arguments.argument(1)

            // 1. Let calendar be the this value.
            // 2. Perform ? RequireInternalSlot(calendar, [[InitializedTemporalCalendar]]).
            val calendar = thisCalendar(arguments.thisValue, "monthDayFromFields")

            // 3. Assert: calendar.[[Identifier]] is "iso8601".
            ecmaAssert(calendar[Slot.Identifier] == "iso8601")

            // 4. If Type(fields) is not Object, throw a TypeError exception.
            if (fields !is JSObject)
                Errors.TODO("Calendar.prototype.dateFromFields").throwTypeError()

            // 5. Set options to ? GetOptionsObject(options).
            options = TemporalAOs.getOptionsObject(options)

            // 6. Let result be ? ISOMonthDayFromFields(fields, options).
            val result = TemporalAOs.isoMonthDayFromFields(fields, options)

            // 7. Return ? CreateTemporalDate(result.[[Year]], result.[[Month]], result.[[Day]], calendar).
            return TemporalAOs.createTemporalMonthDay(result.month, result.day, calendar, result.referenceISODay)
        }

        @JvmStatic
        @ECMAImpl("12.5.7")
        fun dateAdd(arguments: JSArguments): JSValue {
            var date = arguments.argument(0)
            var duration = arguments.argument(1)
            var options = arguments.argument(2)

            // 1. Let calendar be the this value.
            // 2. Perform ? RequireInternalSlot(calendar, [[InitializedTemporalCalendar]]).
            val calendar = thisCalendar(arguments.thisValue, "dateAdd")

            // 3. Assert: calendar.[[Identifier]] is "iso8601".
            ecmaAssert(calendar[Slot.Identifier] == "iso8601")

            // 4. Set date to ? ToTemporalDate(date).
            date = TemporalAOs.toTemporalDate(date)

            // 5. Set duration to ? ToTemporalDuration(duration).
            duration = TemporalAOs.toTemporalDuration(duration)

            // 6. Set options to ? GetOptionsObject(options).
            options = TemporalAOs.getOptionsObject(options)

            // 7. Let overflow be ? ToTemporalOverflow(options).
            val overflow = TemporalAOs.toTemporalOverflow(options)

            // 8. Let balanceResult be ? BalanceDuration(duration.[[Days]], duration.[[Hours]], duration.[[Minutes]], duration.[[Seconds]], duration.[[Milliseconds]], duration.[[Microseconds]], duration.[[Nanoseconds]], "day").
            val balanceResult = TemporalAOs.balanceDuration(duration[Slot.Days], duration[Slot.Hours], duration[Slot.Minutes], duration[Slot.Seconds], duration[Slot.Milliseconds], duration[Slot.Microseconds], duration[Slot.Nanoseconds], "day")

            // 9. Let result be ? AddISODate(date.[[ISOYear]], date.[[ISOMonth]], date.[[ISODay]], duration.[[Years]], duration.[[Months]], duration.[[Weeks]], balanceResult.[[Days]], overflow).
            val result = TemporalAOs.addISODate(date[Slot.ISOYear], date[Slot.ISOMonth], date[Slot.ISODay], duration[Slot.Years], duration[Slot.Months], duration[Slot.Weeks], balanceResult.days, overflow)

            // 10. Return ? CreateTemporalDate(result.[[Year]], result.[[Month]], result.[[Day]], calendar).
            return TemporalAOs.createTemporalDate(result.year, result.month, result.day, calendar)
        }

        @JvmStatic
        @ECMAImpl("12.5.8")
        fun dateUntil(arguments: JSArguments): JSValue {
            var one = arguments.argument(0)
            var two = arguments.argument(1)
            var options = arguments.argument(2)

            // 1. Let calendar be the this value.
            // 2. Perform ? RequireInternalSlot(calendar, [[InitializedTemporalCalendar]]).
            val calendar = thisCalendar(arguments.thisValue, "dateUntil")

            // 3. Assert: calendar.[[Identifier]] is "iso8601".
            ecmaAssert(calendar[Slot.Identifier] == "iso8601")

            // 4. Set one to ? ToTemporalDate(one).
            one = TemporalAOs.toTemporalDate(one)

            // 5. Set two to ? ToTemporalDate(two).
            two = TemporalAOs.toTemporalDate(two)

            // 6. Set options to ? GetOptionsObject(options).
            options = TemporalAOs.getOptionsObject(options)

            // 7. Let largestUnit be ? GetTemporalUnit(options, "largestUnit", date, "auto").
            var largestUnit = TemporalAOs.getTemporalUnit(options, "largestUnit".key(), "date", TemporalAOs.TemporalUnitDefault.Value("auto".toValue()))!!

            // 8. If largestUnit is "auto", set largestUnit to "day".
            if (largestUnit == "auto")
                largestUnit = "day"

            // 9. Let result be DifferenceISODate(one.[[ISOYear]], one.[[ISOMonth]], one.[[ISODay]], two.[[ISOYear]], two.[[ISOMonth]], two.[[ISODay]], largestUnit).
            val result = TemporalAOs.differenceISODate(one[Slot.ISOYear], one[Slot.ISOMonth], one[Slot.ISODay], two[Slot.ISOYear], two[Slot.ISOMonth], two[Slot.ISODay], largestUnit)

            // 10. Return ! CreateTemporalDuration(result.[[Years]], result.[[Months]], result.[[Weeks]], result.[[Days]], 0, 0, 0, 0, 0, 0).
            return TemporalAOs.createTemporalDuration(TemporalAOs.DurationRecord(
               result.years, result.months, result.weeks, result.days, 0, 0, 0, 0, 0, BigInteger.ZERO,
            ))
        }

        @JvmStatic
        @ECMAImpl("12.5.9")
        fun year(arguments: JSArguments): JSValue {
            var temporalDateLike = arguments.argument(0)

            // 1. Let calendar be the this value.
            // 2. Perform ? RequireInternalSlot(calendar, [[InitializedTemporalCalendar]]).
            val calendarValue = thisCalendarId(arguments.thisValue, "year")

            // 3. Assert: calendar.[[Identifier]] is "iso8601".
            ecmaAssert(calendarValue == "iso8601")

            // 4. If Type(temporalDateLike) is not Object or temporalDateLike does not have an [[InitializedTemporalDate]], [[InitializedTemporalDateTime]], or [[InitializedTemporalYearMonth]] internal slot, then
            if (temporalDateLike !is JSObject || temporalDateLike.let { Slot.InitializedTemporalDate !in it && Slot.InitializedTemporalDateTime !in it && Slot.InitializedTemporalYearMonth !in it }) {
                // a. Set temporalDateLike to ? ToTemporalDate(temporalDateLike).
                temporalDateLike = TemporalAOs.toTemporalDate(temporalDateLike)
            }

            // 5. Assert: temporalDateLike has an [[ISOYear]] internal slot.
            ecmaAssert(temporalDateLike is JSObject && Slot.ISOYear in temporalDateLike)

            // 6. Return 𝔽(temporalDateLike.[[ISOYear]]).
            return temporalDateLike[Slot.ISOYear].toValue()
        }

        @JvmStatic
        @ECMAImpl("12.5.10")
        fun month(arguments: JSArguments): JSValue {
            var temporalDateLike = arguments.argument(0)

            // 1. Let calendar be the this value.
            // 2. Perform ? RequireInternalSlot(calendar, [[InitializedTemporalCalendar]]).
            val calendarValue = thisCalendarId(arguments.thisValue, "month")

            // 3. Assert: calendar.[[Identifier]] is "iso8601".
            ecmaAssert(calendarValue == "iso8601")

            // 4. If Type(temporalDateLike) is Object and temporalDateLike has an [[InitializedTemporalMonthDay]] internal slot, then
            if (temporalDateLike is JSObject && Slot.InitializedTemporalMonthDay in temporalDateLike) {
                // a. Throw a TypeError exception.
                Errors.TODO("Calendar.prototype.month").throwTypeError()
            }

            // 5. If Type(temporalDateLike) is not Object or temporalDateLike does not have an [[InitializedTemporalDate]], [[InitializedTemporalDateTime]], or [[InitializedTemporalYearMonth]] internal slot, then
            if (temporalDateLike !is JSObject || temporalDateLike.let { Slot.InitializedTemporalDate !in it && Slot.InitializedTemporalDateTime !in it && Slot.InitializedTemporalYearMonth !in it }) {
                // a. Set temporalDateLike to ? ToTemporalDate(temporalDateLike).
                temporalDateLike = TemporalAOs.toTemporalDate(temporalDateLike)
            }

            // 6. Assert: temporalDateLike has an [[ISOMonth]] internal slot.
            ecmaAssert(temporalDateLike is JSObject && Slot.ISOMonth in temporalDateLike)

            // 7. Return 𝔽(temporalDateLike.[[ISOMonth]]).
            return temporalDateLike[Slot.ISOMonth].toValue()
        }

        @JvmStatic
        @ECMAImpl("12.5.11")
        fun monthCode(arguments: JSArguments): JSValue {
            var temporalDateLike = arguments.argument(0)

            // 1. Let calendar be the this value.
            // 2. Perform ? RequireInternalSlot(calendar, [[InitializedTemporalCalendar]]).
            val calendarValue = thisCalendarId(arguments.thisValue, "monthCode")

            // 3. Assert: calendar.[[Identifier]] is "iso8601".
            ecmaAssert(calendarValue == "iso8601")

            // 4. If Type(temporalDateLike) is not Object or temporalDateLike does not have an [[InitializedTemporalDate]], [[InitializedTemporalDateTime]], or [[InitializedTemporalYearMonth]] internal slot, then
            if (temporalDateLike !is JSObject || temporalDateLike.let { Slot.InitializedTemporalDate !in it && Slot.InitializedTemporalDateTime !in it && Slot.InitializedTemporalYearMonth !in it }) {
                // a. Set temporalDateLike to ? ToTemporalDate(temporalDateLike).
                temporalDateLike = TemporalAOs.toTemporalDate(temporalDateLike)
            }

            // 5. Assert: temporalDateLike has an [[ISOMonth]] internal slot.
            ecmaAssert(temporalDateLike is JSObject && Slot.ISOMonth in temporalDateLike)

            // 6. Return ISOMonthCode(temporalDateLike.[[ISOMonth]]).
            return TemporalAOs.isoMonthCode(temporalDateLike[Slot.ISOMonth]).toValue()
        }

        @JvmStatic
        @ECMAImpl("12.5.12")
        fun day(arguments: JSArguments): JSValue {
            var temporalDateLike = arguments.argument(0)

            // 1. Let calendar be the this value.
            // 2. Perform ? RequireInternalSlot(calendar, [[InitializedTemporalCalendar]]).
            val calendarValue = thisCalendarId(arguments.thisValue, "day")

            // 3. Assert: calendar.[[Identifier]] is "iso8601".
            ecmaAssert(calendarValue == "iso8601")

            // 4. If Type(temporalDateLike) is not Object or temporalDateLike does not have an [[InitializedTemporalDate]], [[InitializedTemporalDateTime]], or [[InitializedTemporalYearMonth]] internal slot, then
            if (temporalDateLike !is JSObject || temporalDateLike.let { Slot.InitializedTemporalDate !in it && Slot.InitializedTemporalDateTime !in it && Slot.InitializedTemporalYearMonth !in it }) {
                // a. Set temporalDateLike to ? ToTemporalDate(temporalDateLike).
                temporalDateLike = TemporalAOs.toTemporalDate(temporalDateLike)
            }

            // 5. Assert: temporalDateLike has an [[ISODay]] internal slot.
            ecmaAssert(temporalDateLike is JSObject && Slot.ISODay in temporalDateLike)

            // 6. Return 𝔽(temporalDateLike.[[ISODay]]).
            return temporalDateLike[Slot.ISODay].toValue()
        }

        @JvmStatic
        @ECMAImpl("12.5.13")
        fun dayOfWeek(arguments: JSArguments): JSValue {
            var temporalDateLike = arguments.argument(0)

            // 1. Let calendar be the this value.
            // 2. Perform ? RequireInternalSlot(calendar, [[InitializedTemporalCalendar]]).
            val calendarValue = thisCalendarId(arguments.thisValue, "dayOfWeek")

            // 3. Assert: calendar.[[Identifier]] is "iso8601".
            ecmaAssert(calendarValue == "iso8601")

            // 4. Let temporalDate be ? ToTemporalDate(temporalDateLike).
            val temporalDate = TemporalAOs.toTemporalDate(temporalDateLike)

            // 5. Return 𝔽(ToISODayOfWeek(temporalDate.[[ISOYear]], temporalDate.[[ISOMonth]], temporalDate.[[ISODay]])).
            return TemporalAOs.toISODayOfWeek(temporalDate[Slot.ISOYear], temporalDate[Slot.ISOMonth], temporalDate[Slot.ISODay]).toValue()
        }

        @JvmStatic
        @ECMAImpl("12.5.14")
        fun dayOfYear(arguments: JSArguments): JSValue {
            var temporalDateLike = arguments.argument(0)

            // 1. Let calendar be the this value.
            // 2. Perform ? RequireInternalSlot(calendar, [[InitializedTemporalCalendar]]).
            val calendarValue = thisCalendarId(arguments.thisValue, "dayOfYear")

            // 3. Assert: calendar.[[Identifier]] is "iso8601".
            ecmaAssert(calendarValue == "iso8601")

            // 4. Let temporalDate be ? ToTemporalDate(temporalDateLike).
            val temporalDate = TemporalAOs.toTemporalDate(temporalDateLike)

            // 5. Return 𝔽(ToISODayOfYear(temporalDate.[[ISOYear]], temporalDate.[[ISOMonth]], temporalDate.[[ISODay]])).
            return TemporalAOs.toISODayOfYear(temporalDate[Slot.ISOYear], temporalDate[Slot.ISOMonth], temporalDate[Slot.ISODay]).toValue()
        }

        @JvmStatic
        @ECMAImpl("12.5.15")
        fun weekOfYear(arguments: JSArguments): JSValue {
            var temporalDateLike = arguments.argument(0)

            // 1. Let calendar be the this value.
            // 2. Perform ? RequireInternalSlot(calendar, [[InitializedTemporalCalendar]]).
            val calendarValue = thisCalendarId(arguments.thisValue, "weekOfYear")

            // 3. Assert: calendar.[[Identifier]] is "iso8601".
            ecmaAssert(calendarValue == "iso8601")

            // 4. Let temporalDate be ? ToTemporalDate(temporalDateLike).
            val temporalDate = TemporalAOs.toTemporalDate(temporalDateLike)

            // 5. Return 𝔽(ToISOWeekOfYear(temporalDate.[[ISOYear]], temporalDate.[[ISOMonth]], temporalDate.[[ISODay]])).
            return TemporalAOs.toISOWeekOfYear(temporalDate[Slot.ISOYear], temporalDate[Slot.ISOMonth], temporalDate[Slot.ISODay]).toValue()
        }

        @JvmStatic
        @ECMAImpl("12.5.15")
        fun daysInWeek(arguments: JSArguments): JSValue {
            var temporalDateLike = arguments.argument(0)

            // 1. Let calendar be the this value.
            // 2. Perform ? RequireInternalSlot(calendar, [[InitializedTemporalCalendar]]).
            val calendarValue = thisCalendarId(arguments.thisValue, "daysInWeek")

            // 3. Assert: calendar.[[Identifier]] is "iso8601".
            ecmaAssert(calendarValue == "iso8601")

            // 4. Perform ? ToTemporalDate(temporalDateLike).
            TemporalAOs.toTemporalDate(temporalDateLike)

            // 5. Return 7𝔽.
            return 7.toValue()
        }

        @JvmStatic
        @ECMAImpl("12.5.17")
        fun daysInMonth(arguments: JSArguments): JSValue {
            var temporalDateLike = arguments.argument(0)

            // 1. Let calendar be the this value.
            // 2. Perform ? RequireInternalSlot(calendar, [[InitializedTemporalCalendar]]).
            val calendarValue = thisCalendarId(arguments.thisValue, "daysInMonth")

            // 3. Assert: calendar.[[Identifier]] is "iso8601".
            ecmaAssert(calendarValue == "iso8601")

            // 4. If Type(temporalDateLike) is not Object or temporalDateLike does not have an [[InitializedTemporalDate]], [[InitializedTemporalDateTime]], or [[InitializedTemporalYearMonth]] internal slot, then
            if (temporalDateLike !is JSObject || temporalDateLike.let { Slot.InitializedTemporalDate !in it && Slot.InitializedTemporalDateTime !in it && Slot.InitializedTemporalYearMonth !in it }) {
                // a. Set temporalDateLike to ? ToTemporalDate(temporalDateLike).
                temporalDateLike = TemporalAOs.toTemporalDate(temporalDateLike)
            }

            // 5. Return 𝔽(! ISODaysInMonth(temporalDateLike.[[ISOYear]], temporalDateLike.[[ISOMonth]])).
            expect(temporalDateLike is JSObject)
            return TemporalAOs.isoDaysInMonth(temporalDateLike[Slot.ISOYear], temporalDateLike[Slot.ISOMonth]).toValue()
        }

        @JvmStatic
        @ECMAImpl("12.5.18")
        fun daysInYear(arguments: JSArguments): JSValue {
            var temporalDateLike = arguments.argument(0)

            // 1. Let calendar be the this value.
            // 2. Perform ? RequireInternalSlot(calendar, [[InitializedTemporalCalendar]]).
            val calendarValue = thisCalendarId(arguments.thisValue, "daysInYear")

            // 3. Assert: calendar.[[Identifier]] is "iso8601".
            ecmaAssert(calendarValue == "iso8601")

            // 4. If Type(temporalDateLike) is not Object or temporalDateLike does not have an [[InitializedTemporalDate]], [[InitializedTemporalDateTime]], or [[InitializedTemporalYearMonth]] internal slot, then
            if (temporalDateLike !is JSObject || temporalDateLike.let { Slot.InitializedTemporalDate !in it && Slot.InitializedTemporalDateTime !in it && Slot.InitializedTemporalYearMonth !in it }) {
                // a. Set temporalDateLike to ? ToTemporalDate(temporalDateLike).
                temporalDateLike = TemporalAOs.toTemporalDate(temporalDateLike)
            }

            // 5. Return DaysInYear(𝔽(temporalDateLike.[[ISOYear]])).
            expect(temporalDateLike is JSObject)
            return AOs.daysInYear(temporalDateLike[Slot.ISOYear]).toValue()
        }

        @JvmStatic
        @ECMAImpl("12.5.19")
        fun monthsInYear(arguments: JSArguments): JSValue {
            val temporalDateLike = arguments.argument(0)

            // 1. Let calendar be the this value.
            // 2. Perform ? RequireInternalSlot(calendar, [[InitializedTemporalCalendar]]).
            val calendarValue = thisCalendarId(arguments.thisValue, "monthsInYear")

            // 3. Assert: calendar.[[Identifier]] is "iso8601".
            ecmaAssert(calendarValue == "iso8601")

            // 4. If Type(temporalDateLike) is not Object or temporalDateLike does not have an [[InitializedTemporalDate]], [[InitializedTemporalDateTime]], or [[InitializedTemporalYearMonth]] internal slot, then
            if (temporalDateLike !is JSObject || temporalDateLike.let { Slot.InitializedTemporalDate !in it && Slot.InitializedTemporalDateTime !in it && Slot.InitializedTemporalYearMonth !in it }) {
                // a. Perform ? ToTemporalDate(temporalDateLike).
                TemporalAOs.toTemporalDate(temporalDateLike)
            }

            // 5. Return 12𝔽.
            return 12.toValue()
        }

        @JvmStatic
        @ECMAImpl("12.5.20")
        fun inLeapYear(arguments: JSArguments): JSValue {
            var temporalDateLike = arguments.argument(0)

            // 1. Let calendar be the this value.
            // 2. Perform ? RequireInternalSlot(calendar, [[InitializedTemporalCalendar]]).
            val calendarValue = thisCalendarId(arguments.thisValue, "inLeapYear")

            // 3. Assert: calendar.[[Identifier]] is "iso8601".
            ecmaAssert(calendarValue == "iso8601")

            // 4. If Type(temporalDateLike) is not Object or temporalDateLike does not have an [[InitializedTemporalDate]], [[InitializedTemporalDateTime]], or [[InitializedTemporalYearMonth]] internal slot, then
            if (temporalDateLike !is JSObject || temporalDateLike.let { Slot.InitializedTemporalDate !in it && Slot.InitializedTemporalDateTime !in it && Slot.InitializedTemporalYearMonth !in it }) {
                // a. Set temporalDateLike to ? ToTemporalDate(temporalDateLike).
                temporalDateLike = TemporalAOs.toTemporalDate(temporalDateLike)
            }

            // 5. If InLeapYear(TimeFromYear(𝔽(temporalDateLike.[[ISOYear]]))) is 1𝔽, return true.
            // 6. Return false.
            expect(temporalDateLike is JSObject)
            return (AOs.inLeapYear(AOs.timeFromYear(temporalDateLike[Slot.ISOYear])) == 1).toValue()
        }

        @JvmStatic
        @ECMAImpl("12.5.21")
        fun fields(arguments: JSArguments): JSValue {
            val fields = arguments.argument(0)

            // 1. Let calendar be the this value.
            // 2. Perform ? RequireInternalSlot(calendar, [[InitializedTemporalCalendar]]).
            val calendarValue = thisCalendarId(arguments.thisValue, "fields")

            // 3. Assert: calendar.[[Identifier]] is "iso8601".
            ecmaAssert(calendarValue == "iso8601")

            // 4. Let iteratorRecord be ? GetIterator(fields, sync).
            val iteratorRecord = AOs.getIterator(fields, AOs.IteratorHint.Sync)

            // 5. Let fieldNames be a new empty List.
            val fieldNames = mutableListOf<String>()

            // 6. Let next be true.
            var next = true

            // 7. Repeat, while next is not false,
            while (next) {
                // a. Set next to ? IteratorStep(iteratorRecord).
                val next = AOs.iteratorStep(iteratorRecord)

                // b. If next is not false, then
                if (next != JSFalse) {
                    // i. Let nextValue be ? IteratorValue(next).
                    val nextValue = AOs.iteratorValue(next)

                    // ii. If Type(nextValue) is not String, then
                    if (nextValue !is JSString) {
                        // 1. Let completion be ThrowCompletion(a newly created TypeError object).
                        val completion = completion<JSValue> { Errors.TODO("Calendar.prototype.fields 1").throwTypeError() }

                        // 2. Return ? IteratorClose(iteratorRecord, completion).
                        return AOs.iteratorClose(iteratorRecord, completion)
                    }

                    // iii. If fieldNames contains nextValue, then
                    if (nextValue.string in fieldNames) {
                        // 1. Let completion be ThrowCompletion(a newly created RangeError object).
                        val completion = completion<JSValue> { Errors.TODO("Calendar.prototype.fields 2").throwRangeError() }

                        // 2. Return ? IteratorClose(iteratorRecord, completion).
                        return AOs.iteratorClose(iteratorRecord, completion)
                    }

                    // iv. If nextValue is not one of "year", "month", "monthCode", "day", "hour", "minute", "second", "millisecond", "microsecond", "nanosecond", then
                    if (nextValue.string !in setOf("year", "month", "monthCode", "day", "hour", "minute", "second", "millisecond", "microsecond", "nanosecond")) {
                        // 1. Let completion be ThrowCompletion(a newly created RangeError object).
                        val completion = completion<JSValue> { Errors.TODO("Calendar.prototype.fields 3").throwRangeError() }

                        // 2. Return ? IteratorClose(iteratorRecord, completion).
                        return AOs.iteratorClose(iteratorRecord, completion)
                    }

                    // v. Append nextValue to the end of the List fieldNames.
                    fieldNames.add(nextValue.string)
                }
            }

            // 8. Return CreateArrayFromList(fieldNames).
            return AOs.createArrayFromList(fieldNames.map { it.toValue() })
        }

        @JvmStatic
        @ECMAImpl("12.5.22")
        fun mergeFields(arguments: JSArguments): JSValue {            
            // 1. Let calendar be the this value.
            // 2. Perform ? RequireInternalSlot(calendar, [[InitializedTemporalCalendar]]).
            val calendarValue = thisCalendarId(arguments.thisValue, "mergeFields")
            
            // 3. Set fields to ? ToObject(fields).
            val fields = arguments.argument(0).toObject()

            // 4. Set additionalFields to ? ToObject(additionalFields).
            val additionalFields = arguments.argument(1).toObject()

            // 5. Assert: calendar.[[Identifier]] is "iso8601".
            ecmaAssert(calendarValue == "iso8601")

            // 6. Return ? DefaultMergeCalendarFields(fields, additionalFields).
            return TemporalAOs.defaultMergeCalendarFields(fields, additionalFields)
        }

        @JvmStatic
        @ECMAImpl("12.5.23")
        fun toString(arguments: JSArguments): JSValue {
            // 1. Let calendar be the this value.
            // 2. Perform ? RequireInternalSlot(calendar, [[InitializedTemporalCalendar]]).
            // 3. Return calendar.[[Identifier]].
            return thisCalendarId(arguments.thisValue, "toString").toValue()
        }

        @JvmStatic
        @ECMAImpl("12.5.23")
        fun toJSON(arguments: JSArguments): JSValue {
            // 1. Let calendar be the this value.
            // 2. Perform ? RequireInternalSlot(calendar, [[InitializedTemporalCalendar]]).
            // 3. Return ? ToString(calendar).
            return thisCalendar(arguments.thisValue, "toJSON").toJSString()
        }
    }
}
