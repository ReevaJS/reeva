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

class JSPlainYearMonthProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("constructor", realm.plainYearMonthCtor)
        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "Temporal.PlainYearMonth".toValue(), attrs { +conf })

        defineBuiltinGetter("calendar", ::getCalendar)
        defineBuiltinGetter("year", ::getYear)
        defineBuiltinGetter("month", ::getMonth)
        defineBuiltinGetter("monthCode", ::getMonthCode)
        defineBuiltinGetter("daysInYear", ::getDaysInYear)
        defineBuiltinGetter("daysInMonth", ::getDaysInMonth)
        defineBuiltinGetter("monthsInYear", ::getMonthsInYear)
        defineBuiltinGetter("inLeapYear", ::getInLeapYear)

        defineBuiltin("with", 1, ::with)
        defineBuiltin("add", 1, ::add)
        defineBuiltin("subtract", 1, ::subtract)
        defineBuiltin("until", 1, ::until)
        defineBuiltin("since", 1, ::since)
        defineBuiltin("equals", 1, ::equals)
        defineBuiltin("toString", 0, ::toString)
        defineBuiltin("toJSON", 0, ::toJSON)
        defineBuiltin("valueOf", 0, ::valueOf)
        defineBuiltin("toPlainDate", 1, ::toPlainDate)
        defineBuiltin("getISOFields", 1, ::getISOFields)
    }

    companion object {
        @JvmStatic
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSPlainYearMonthProto(realm).initialize()

        private fun thisPlainYearMonth(thisValue: JSValue, method: String): JSObject {
            if (!AOs.requireInternalSlot(thisValue, Slot.InitializedTemporalYearMonth))
                Errors.IncompatibleMethodCall("PlainYearMonth.prototype.$method").throwTypeError()
            return thisValue
        }

        @JvmStatic
        @ECMAImpl("9.3.3")
        fun getCalendar(arguments: JSArguments): JSValue {
            // 1. Let yearMonth be the this value.
            // 2. Perform ? RequireInternalSlot(yearMonth, [[InitializedTemporalYearMonth]]).
            // 3. Return yearMonth.[[Calendar]].
            return thisPlainYearMonth(arguments.thisValue, "get calendar")[Slot.Calendar]
        }

        @JvmStatic
        @ECMAImpl("9.3.4")
        fun getYear(arguments: JSArguments): JSValue {
            // 1. Let yearMonth be the this value.
            // 2. Perform ? RequireInternalSlot(yearMonth, [[InitializedTemporalYearMonth]]).
            val yearMonth = thisPlainYearMonth(arguments.thisValue, "get year")

            // 3. Let calendar be yearMonth.[[Calendar]].
            val calendar = yearMonth[Slot.Calendar]

            // 4. Return 𝔽(? CalendarYear(calendar, yearMonth)).
            return TemporalAOs.calendarYear(calendar, yearMonth)
        }

        @JvmStatic
        @ECMAImpl("9.3.5")
        fun getMonth(arguments: JSArguments): JSValue {
            // 1. Let yearMonth be the this value.
            // 2. Perform ? RequireInternalSlot(yearMonth, [[InitializedTemporalYearMonth]]).
            val yearMonth = thisPlainYearMonth(arguments.thisValue, "get month")

            // 3. Let calendar be yearMonth.[[Calendar]].
            val calendar = yearMonth[Slot.Calendar]

            // 4. Return 𝔽(? CalendarMonth(calendar, yearMonth)).
            return TemporalAOs.calendarMonth(calendar, yearMonth)
        }

        @JvmStatic
        @ECMAImpl("9.3.6")
        fun getMonthCode(arguments: JSArguments): JSValue {
            // 1. Let yearMonth be the this value.
            // 2. Perform ? RequireInternalSlot(yearMonth, [[InitializedTemporalYearMonth]]).
            val yearMonth = thisPlainYearMonth(arguments.thisValue, "get monthCode")

            // 3. Let calendar be yearMonth.[[Calendar]].
            val calendar = yearMonth[Slot.Calendar]

            // 4. Return 𝔽(? CalendarMonthCode(calendar, yearMonth)).
            return TemporalAOs.calendarMonthCode(calendar, yearMonth)
        }

        @JvmStatic
        @ECMAImpl("9.3.7")
        fun getDaysInYear(arguments: JSArguments): JSValue {
            // 1. Let yearMonth be the this value.
            // 2. Perform ? RequireInternalSlot(yearMonth, [[InitializedTemporalYearMonth]]).
            val yearMonth = thisPlainYearMonth(arguments.thisValue, "get daysInYear")

            // 3. Let calendar be yearMonth.[[Calendar]].
            val calendar = yearMonth[Slot.Calendar]

            // 4. Return 𝔽(? CalendarDaysInYear(calendar, yearMonth)).
            return TemporalAOs.calendarDaysInYear(calendar, yearMonth)
        }

        @JvmStatic
        @ECMAImpl("9.3.8")
        fun getDaysInMonth(arguments: JSArguments): JSValue {
            // 1. Let yearMonth be the this value.
            // 2. Perform ? RequireInternalSlot(yearMonth, [[InitializedTemporalYearMonth]]).
            val yearMonth = thisPlainYearMonth(arguments.thisValue, "get daysInMonth")

            // 3. Let calendar be yearMonth.[[Calendar]].
            val calendar = yearMonth[Slot.Calendar]

            // 4. Return 𝔽(? CalendarDaysInMonth(calendar, yearMonth)).
            return TemporalAOs.calendarDaysInMonth(calendar, yearMonth)
        }

        @JvmStatic
        @ECMAImpl("9.3.9")
        fun getMonthsInYear(arguments: JSArguments): JSValue {
            // 1. Let yearMonth be the this value.
            // 2. Perform ? RequireInternalSlot(yearMonth, [[InitializedTemporalYearMonth]]).
            val yearMonth = thisPlainYearMonth(arguments.thisValue, "get monthsInYear")

            // 3. Let calendar be yearMonth.[[Calendar]].
            val calendar = yearMonth[Slot.Calendar]

            // 4. Return 𝔽(? CalendarMonthsInYear(calendar, yearMonth)).
            return TemporalAOs.calendarMonthsInYear(calendar, yearMonth)
        }

        @JvmStatic
        @ECMAImpl("9.3.10")
        fun getInLeapYear(arguments: JSArguments): JSValue {
            // 1. Let yearMonth be the this value.
            // 2. Perform ? RequireInternalSlot(yearMonth, [[InitializedTemporalYearMonth]]).
            val yearMonth = thisPlainYearMonth(arguments.thisValue, "get inLeapYear")

            // 3. Let calendar be yearMonth.[[Calendar]].
            val calendar = yearMonth[Slot.Calendar]

            // 4. Return 𝔽(? CalendarInLeapYear(calendar, yearMonth)).
            return TemporalAOs.calendarInLeapYear(calendar, yearMonth)
        }

        @JvmStatic
        @ECMAImpl("9.3.11")
        fun with(arguments: JSArguments): JSValue {
            val temporalYearMonthLike = arguments.argument(0)

            // 1. Let yearMonth be the this value.
            // 2. Perform ? RequireInternalSlot(yearMonth, [[InitializedTemporalYearMonth]]).
            val yearMonth = thisPlainYearMonth(arguments.thisValue, "with")

            // 3. If Type(temporalYearMonthLike) is not Object, then
            if (temporalYearMonthLike !is JSObject) {
                // a. Throw a TypeError exception.
                Errors.TODO("PlainYearMonth.prototype.with").throwTypeError()
            }

            // 4. Perform ? RejectObjectWithCalendarOrTimeZone(temporalYearMonthLike).
            TemporalAOs.rejectObjectWithCalendarOrTimeZone(temporalYearMonthLike)

            // 5. Let calendar be yearMonth.[[Calendar]].
            val calendar = yearMonth[Slot.Calendar]

            // 6. Let fieldNames be ? CalendarFields(calendar, « "month", "monthCode", "year" »).
            val fieldNames = TemporalAOs.calendarFields(calendar, listOf("month", "monthCode", "year"))

            // 7. Let partialYearMonth be ? PrepareTemporalFields(temporalYearMonthLike, fieldNames, partial).
            val partialYearMonth = TemporalAOs.prepareTemporalFields(temporalYearMonthLike, fieldNames, null)

            // 8. Set options to ? GetOptionsObject(options).
            val options = TemporalAOs.getOptionsObject(arguments.argument(1))

            // 9. Let fields be ? PrepareTemporalFields(yearMonth, fieldNames, «»).
            var fields = TemporalAOs.prepareTemporalFields(yearMonth, fieldNames, emptySet())

            // 10. Set fields to ? CalendarMergeFields(calendar, fields, partialYearMonth).
            fields = TemporalAOs.calendarMergeFields(calendar, fields, partialYearMonth)

            // 11. Set fields to ? PrepareTemporalFields(fields, fieldNames, «»).
            fields = TemporalAOs.prepareTemporalFields(fields, fieldNames, emptySet())

            // 12. Return ? CalendarYearMonthFromFields(calendar, fields, options).
            return TemporalAOs.calendarYearMonthFromFields(calendar, fields, options)
        }

        @JvmStatic
        @ECMAImpl("9.3.12")
        fun add(arguments: JSArguments): JSValue {
            // 1. Let yearMonth be the this value.
            // 2. Perform ? RequireInternalSlot(yearMonth, [[InitializedTemporalYearMonth]]).
            val yearMonth = thisPlainYearMonth(arguments.thisValue, "add")

            // 3. Return ? AddDurationToOrSubtractDurationFromPlainYearMonth(add, yearMonth, temporalDurationLike, options).
            return TemporalAOs.addDurationToOrSubtractDurationFromPlainYearMonth(true, yearMonth, arguments.argument(0), arguments.argument(1))
        }

        @JvmStatic
        @ECMAImpl("9.3.13")
        fun subtract(arguments: JSArguments): JSValue {
            // 1. Let yearMonth be the this value.
            // 2. Perform ? RequireInternalSlot(yearMonth, [[InitializedTemporalYearMonth]]).
            val yearMonth = thisPlainYearMonth(arguments.thisValue, "subtract")

            // 3. Return ? AddDurationToOrSubtractDurationFromPlainYearMonth(subtract, yearMonth, temporalDurationLike, options).
            return TemporalAOs.addDurationToOrSubtractDurationFromPlainYearMonth(false, yearMonth, arguments.argument(0), arguments.argument(1))
        }

        @JvmStatic
        @ECMAImpl("9.3.14")
        fun until(arguments: JSArguments): JSValue {
            // 1. Let yearMonth be the this value.
            // 2. Perform ? RequireInternalSlot(yearMonth, [[InitializedTemporalYearMonth]]).
            val yearMonth = thisPlainYearMonth(arguments.thisValue, "until")

            // 3. Return ? DifferenceTemporalPlainYearMonth(until, yearMonth, other, options).
            return TemporalAOs.differenceTemporalPlainYearMonth(true, yearMonth, arguments.argument(0), arguments.argument(1))
        }

        @JvmStatic
        @ECMAImpl("9.3.15")
        fun since(arguments: JSArguments): JSValue {
            // 1. Let yearMonth be the this value.
            // 2. Perform ? RequireInternalSlot(yearMonth, [[InitializedTemporalYearMonth]]).
            val yearMonth = thisPlainYearMonth(arguments.thisValue, "since")

            // 3. Return ? DifferenceTemporalPlainYearMonth(since, yearMonth, other, options).
            return TemporalAOs.differenceTemporalPlainYearMonth(false, yearMonth, arguments.argument(0), arguments.argument(1))
        }

        @JvmStatic
        @ECMAImpl("9.3.16")
        fun equals(arguments: JSArguments): JSValue {
            // 1. Let yearMonth be the this value.
            // 2. Perform ? RequireInternalSlot(yearMonth, [[InitializedTemporalYearMonth]]).
            val yearMonth = thisPlainYearMonth(arguments.thisValue, "equals")

            // 3. Set other to ? ToTemporalYearMonth(other).
            val other = TemporalAOs.toTemporalYearMonth(arguments.argument(0))

            // 4. If yearMonth.[[ISOYear]] ≠ other.[[ISOYear]], return false.
            if (yearMonth[Slot.ISOYear] != other[Slot.ISOYear])
                return JSFalse

            // 5. If yearMonth.[[ISOMonth]] ≠ other.[[ISOMonth]], return false.
            if (yearMonth[Slot.ISOMonth] != other[Slot.ISOMonth])
                return JSFalse

            // 6. If yearMonth.[[ISODay]] ≠ other.[[ISODay]], return false.
            if (yearMonth[Slot.ISODay] != other[Slot.ISODay])
                return JSFalse

            // 7. Return ? CalendarEquals(yearMonth.[[Calendar]], other.[[Calendar]]).
            return TemporalAOs.calendarEquals(yearMonth[Slot.Calendar], other[Slot.Calendar]).toValue()
        }

        @JvmStatic
        @ECMAImpl("9.3.17")
        fun toString(arguments: JSArguments): JSValue {
            // 1. Let yearMonth be the this value.
            // 2. Perform ? RequireInternalSlot(yearMonth, [[InitializedTemporalYearMonth]]).
            val yearMonth = thisPlainYearMonth(arguments.thisValue, "toString")

            // 3. Set options to ? GetOptionsObject(options).
            val options = TemporalAOs.getOptionsObject(arguments.argument(0))

            // 4. Let showCalendar be ? ToShowCalendarOption(options).
            val showCalendar = TemporalAOs.toShowCalendarOption(options)

            // 5. Return ? TemporalYearMonthToString(yearMonth, showCalendar).
            return TemporalAOs.temporalYearMonthToString(yearMonth, showCalendar).toValue()
        }

        @JvmStatic
        @ECMAImpl("9.3.18")
        fun toLocaleString(arguments: JSArguments): JSValue {
            // 1. Let yearMonth be the this value.
            // 2. Perform ? RequireInternalSlot(yearMonth, [[InitializedTemporalYearMonth]]).
            val yearMonth = thisPlainYearMonth(arguments.thisValue, "toLocaleString")

            // 3. Return ? TemporalYearMonthToString(yearMonth, "auto").
            return TemporalAOs.temporalYearMonthToString(yearMonth, "auto").toValue()
        }

        @JvmStatic
        @ECMAImpl("9.3.19")
        fun toJSON(arguments: JSArguments): JSValue {
            // 1. Let yearMonth be the this value.
            // 2. Perform ? RequireInternalSlot(yearMonth, [[InitializedTemporalYearMonth]]).
            val yearMonth = thisPlainYearMonth(arguments.thisValue, "toJSON")

            // 3. Return ? TemporalYearMonthToString(yearMonth, "auto").
            return TemporalAOs.temporalYearMonthToString(yearMonth, "auto").toValue()
        }

        @JvmStatic
        @ECMAImpl("9.3.20")
        fun valueOf(arguments: JSArguments): JSValue {
            // 1. Throw a TypeError exception.
            Errors.TODO("PlainYearMonth.prototype.valueOf").throwTypeError()
        }

        @JvmStatic
        @ECMAImpl("9.3.21")
        fun toPlainDate(arguments: JSArguments): JSValue {
            val item = arguments.argument(0)

            // 1. Let yearMonth be the this value.
            // 2. Perform ? RequireInternalSlot(yearMonth, [[InitializedTemporalYearMonth]]).
            val yearMonth = thisPlainYearMonth(arguments.thisValue, "toPlainDate")

            // 3. If Type(item) is not Object, then
            if (item !is JSObject) {
                // a. Throw a TypeError exception.
                Errors.TODO("PlainYearMonth.prototype.toPlainDate").throwTypeError()
            }

            // 4. Let calendar be yearMonth.[[Calendar]].
            val calendar = yearMonth[Slot.Calendar]

            // 5. Let receiverFieldNames be ? CalendarFields(calendar, « "monthCode", "year" »).
            val receiverFieldNames = TemporalAOs.calendarFields(calendar, listOf("monthCode", "year"))

            // 6. Let fields be ? PrepareTemporalFields(yearMonth, receiverFieldNames, «»).
            val fields = TemporalAOs.prepareTemporalFields(yearMonth, receiverFieldNames, emptySet())

            // 7. Let inputFieldNames be ? CalendarFields(calendar, « "day" »).
            val inputFieldNames = TemporalAOs.calendarFields(calendar, listOf("day"))

            // 8. Let inputFields be ? PrepareTemporalFields(item, inputFieldNames, «»).
            val inputFields = TemporalAOs.prepareTemporalFields(item, inputFieldNames, emptySet())

            // 9. Let mergedFields be ? CalendarMergeFields(calendar, fields, inputFields).
            var mergedFields = TemporalAOs.calendarMergeFields(calendar, fields, inputFields)

            // 10. Let mergedFieldNames be MergeLists(receiverFieldNames, inputFieldNames).
            val mergedFieldNames = (receiverFieldNames + inputFieldNames).distinct()

            // 11. Set mergedFields to ? PrepareTemporalFields(mergedFields, mergedFieldNames, «»).
            mergedFields = TemporalAOs.prepareTemporalFields(mergedFields, mergedFieldNames, emptySet())

            // 12. Let options be OrdinaryObjectCreate(null).
            val options = JSObject.create()

            // 13. Perform ! CreateDataPropertyOrThrow(options, "overflow", "reject").
            AOs.createDataPropertyOrThrow(options, "overflow".key(), "reject".toValue())

            // 14. Return ? CalendarDateFromFields(calendar, mergedFields, options).
            return TemporalAOs.calendarDateFromFields(calendar, mergedFields, options)
        }

        @JvmStatic
        @ECMAImpl("9.3.22")
        fun getISOFields(arguments: JSArguments): JSValue {
            // 1. Let yearMonth be the this value.
            // 2. Perform ? RequireInternalSlot(yearMonth, [[InitializedTemporalYearMonth]]).
            val yearMonth = thisPlainYearMonth(arguments.thisValue, "getISOFields")
            
            // 3. Let fields be OrdinaryObjectCreate(%Object.prototype%).
            val fields = JSObject.create()

            // 4. Perform ! CreateDataPropertyOrThrow(fields, "calendar", yearMonth.[[Calendar]]).
            AOs.createDataPropertyOrThrow(fields, "calendar".key(), yearMonth[Slot.Calendar])

            // 5. Perform ! CreateDataPropertyOrThrow(fields, "isoDay", 𝔽(yearMonth.[[ISODay]])).
            AOs.createDataPropertyOrThrow(fields, "calendar".key(), yearMonth[Slot.ISODay].toValue())

            // 6. Perform ! CreateDataPropertyOrThrow(fields, "isoMonth", 𝔽(yearMonth.[[ISOMonth]])).
            AOs.createDataPropertyOrThrow(fields, "calendar".key(), yearMonth[Slot.ISOMonth].toValue())

            // 7. Perform ! CreateDataPropertyOrThrow(fields, "isoYear", 𝔽(yearMonth.[[ISOYear]])).
            AOs.createDataPropertyOrThrow(fields, "calendar".key(), yearMonth[Slot.ISOYear].toValue())

            // 8. Return fields.
            return fields
        }
    }
}
