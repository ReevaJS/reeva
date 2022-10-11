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

class JSPlainMonthDayProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("constructor", realm.plainMonthDayCtor)
        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "Temporal.PlainMonthDay".toValue(), attrs { +conf })

        defineBuiltinGetter("calendar", ::getCalendar)
        defineBuiltinGetter("monthCode", ::getMonthCode)
        defineBuiltinGetter("day", ::getDay)

        defineBuiltin("with", 1, ::with)
        defineBuiltin("equals", 1, ::equals)
        defineBuiltin("toString", 0, ::toString)
        defineBuiltin("toLocaleString", 0, ::toLocaleString)
        defineBuiltin("toJSON", 0, ::toJSON)
        defineBuiltin("valueOf", 0, ::valueOf)
        defineBuiltin("toPlainDate", 1, ::toPlainDate)
        defineBuiltin("getISOFields", 0, ::getISOFields)
    }

    companion object {
        @JvmStatic
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSPlainMonthDayProto(realm).initialize()

        private fun thisPlainMonthDay(thisValue: JSValue, method: String): JSObject {
            if (!AOs.requireInternalSlot(thisValue, Slot.InitializedTemporalMonthDay))
                Errors.IncompatibleMethodCall("PlainMonthDay.prototype.$method").throwTypeError()
            return thisValue
        }

        @JvmStatic
        @ECMAImpl("10.3.3")
        fun getCalendar(arguments: JSArguments): JSValue {
            // 1. Let monthDay be the this value.
            // 2. Perform ? RequireInternalSlot(monthDay, [[InitializedTemporalMonthDay]]).
            // 3. Return monthDay.[[Calendar]].
            return thisPlainMonthDay(arguments.thisValue, "get calendar")[Slot.Calendar]
        }

        @JvmStatic
        @ECMAImpl("10.3.4")
        fun getMonthCode(arguments: JSArguments): JSValue {
            // 1. Let monthDay be the this value.
            // 2. Perform ? RequireInternalSlot(monthDay, [[InitializedTemporalMonthDay]]).
            val monthDay = thisPlainMonthDay(arguments.thisValue, "get monthCode")

            // 3. Let calendar be monthDay.[[Calendar]].
            // 4. Return ? CalendarMonthCode(calendar, monthDay).
            return TemporalAOs.calendarMonthCode(monthDay[Slot.Calendar], monthDay)
        }

        @JvmStatic
        @ECMAImpl("10.3.5")
        fun getDay(arguments: JSArguments): JSValue {
            // 1. Let monthDay be the this value.
            // 2. Perform ? RequireInternalSlot(monthDay, [[InitializedTemporalMonthDay]]).
            val monthDay = thisPlainMonthDay(arguments.thisValue, "get day")

            // 3. Let calendar be monthDay.[[Calendar]].
            // 4. Return 𝔽(? CalendarDay(calendar, monthDay)).
            return TemporalAOs.calendarDay(monthDay[Slot.Calendar], monthDay)
        }

        @JvmStatic
        @ECMAImpl("10.3.6")
        fun with(arguments: JSArguments): JSValue {
            val temporalMonthDayLike = arguments.argument(0)

            // 1. Let monthDay be the this value.
            // 2. Perform ? RequireInternalSlot(monthDay, [[InitializedTemporalMonthDay]]).
            val monthDay = thisPlainMonthDay(arguments.thisValue, "with")

            // 3. If Type(temporalMonthDayLike) is not Object, then
            if (temporalMonthDayLike !is JSObject) {
                // a. Throw a TypeError exception.
                Errors.TODO("PlainMonthDay.prototype.with").throwTypeError()
            }

            // 4. Perform ? RejectObjectWithCalendarOrTimeZone(temporalMonthDayLike).
            TemporalAOs.rejectObjectWithCalendarOrTimeZone(temporalMonthDayLike)

            // 5. Let calendar be monthDay.[[Calendar]].
            val calendar = monthDay[Slot.Calendar]

            // 6. Let fieldNames be ? CalendarFields(calendar, « "day", "month", "monthCode", "year" »).
            val fieldNames = TemporalAOs.calendarFields(calendar, listOf("day", "month", "monthCode", "year"))

            // 7. Let partialMonthDay be ? PrepareTemporalFields(temporalMonthDayLike, fieldNames, partial).
            val partialMonthDay = TemporalAOs.prepareTemporalFields(temporalMonthDayLike, fieldNames, null)

            // 8. Set options to ? GetOptionsObject(options).
            val options = TemporalAOs.getOptionsObject(arguments.argument(1))

            // 9. Let fields be ? PrepareTemporalFields(monthDay, fieldNames, «»).
            var fields = TemporalAOs.prepareTemporalFields(monthDay, fieldNames, emptySet())

            // 10. Set fields to ? CalendarMergeFields(calendar, fields, partialMonthDay).
            fields = TemporalAOs.calendarMergeFields(calendar, fields, partialMonthDay)

            // 11. Set fields to ? PrepareTemporalFields(fields, fieldNames, «»).
            fields = TemporalAOs.prepareTemporalFields(fields, fieldNames, emptySet())

            // 12. Return ? CalendarMonthDayFromFields(calendar, fields, options).            
            return TemporalAOs.calendarMonthDayFromFields(calendar, fields, options)
        }

        @JvmStatic
        @ECMAImpl("10.3.7")
        fun equals(arguments: JSArguments): JSValue {
            // 1. Let monthDay be the this value.
            // 2. Perform ? RequireInternalSlot(monthDay, [[InitializedTemporalMonthDay]]).
            val monthDay = thisPlainMonthDay(arguments.thisValue, "equals")

            // 3. Set other to ? ToTemporalMonthDay(other).
            val other = TemporalAOs.toTemporalMonthDay(arguments.argument(0))

            // 4. If monthDay.[[ISOMonth]] ≠ other.[[ISOMonth]], return false.
            if (monthDay[Slot.ISOMonth] != other[Slot.ISOMonth])
                return JSFalse

            // 5. If monthDay.[[ISODay]] ≠ other.[[ISODay]], return false.
            if (monthDay[Slot.ISODay] != other[Slot.ISODay])
                return JSFalse

            // 6. If monthDay.[[ISOYear]] ≠ other.[[ISOYear]], return false.
            if (monthDay[Slot.ISOYear] != other[Slot.ISOYear])
                return JSFalse

            // 7. Return ? CalendarEquals(monthDay.[[Calendar]], other.[[Calendar]]).
            return TemporalAOs.calendarEquals(monthDay[Slot.Calendar], other[Slot.Calendar]).toValue()
        }

        @JvmStatic
        @ECMAImpl("10.3.8")
        fun toString(arguments: JSArguments): JSValue {
            // 1. Let monthDay be the this value.
            // 2. Perform ? RequireInternalSlot(monthDay, [[InitializedTemporalMonthDay]]).
            val monthDay = thisPlainMonthDay(arguments.thisValue, "toString")

            // 3. Set options to ? GetOptionsObject(options).
            val options = TemporalAOs.getOptionsObject(arguments.argument(0))

            // 4. Let showCalendar be ? ToShowCalendarOption(options).
            val showCalendar = TemporalAOs.toShowCalendarOption(options)

            // 5. Return ? TemporalMonthDayToString(monthDay, showCalendar).
            return TemporalAOs.temporalMonthDayToString(monthDay, showCalendar).toValue()
        }

        @JvmStatic
        @ECMAImpl("10.3.9")
        fun toLocaleString(arguments: JSArguments): JSValue {
            // 1. Let monthDay be the this value.
            // 2. Perform ? RequireInternalSlot(monthDay, [[InitializedTemporalMonthDay]]).
            val monthDay = thisPlainMonthDay(arguments.thisValue, "toLocaleString")

            // 3. Return ? TemporalMonthDayToString(monthDay, "auto").
            return TemporalAOs.temporalMonthDayToString(monthDay, "auto").toValue()
        }

        @JvmStatic
        @ECMAImpl("10.3.10")
        fun toJSON(arguments: JSArguments): JSValue {
            // 1. Let monthDay be the this value.
            // 2. Perform ? RequireInternalSlot(monthDay, [[InitializedTemporalMonthDay]]).
            val monthDay = thisPlainMonthDay(arguments.thisValue, "toJSON")

            // 3. Return ? TemporalMonthDayToString(monthDay, "auto").
            return TemporalAOs.temporalMonthDayToString(monthDay, "auto").toValue()
        }

        @JvmStatic
        @ECMAImpl("10.3.11")
        fun valueOf(arguments: JSArguments): JSValue {
            // 1. Throw a TypeError exception.
            Errors.TODO("PlainMonthDay.prototype.valueOf").throwRangeError()
        }

        @JvmStatic
        @ECMAImpl("10.3.12")
        fun toPlainDate(arguments: JSArguments): JSValue {
            val item = arguments.argument(0)

            // 1. Let monthDay be the this value.
            // 2. Perform ? RequireInternalSlot(monthDay, [[InitializedTemporalMonthDay]]).
            val monthDay = thisPlainMonthDay(arguments.thisValue, "toPlainDate")

            // 3. If Type(item) is not Object, then
            if (item !is JSObject) {
                // a. Throw a TypeError exception.
                Errors.TODO("PlainMonthDay.prototype.toPlainDate").throwTypeError()
            }

            // 4. Let calendar be monthDay.[[Calendar]].
            val calendar = monthDay[Slot.Calendar]

            // 5. Let receiverFieldNames be ? CalendarFields(calendar, « "day", "monthCode" »).
            val receiverFieldNames = TemporalAOs.calendarFields(calendar, listOf("day", "monthCode"))

            // 6. Let fields be ? PrepareTemporalFields(monthDay, receiverFieldNames, «»).
            val fields = TemporalAOs.prepareTemporalFields(monthDay, receiverFieldNames, emptySet())

            // 7. Let inputFieldNames be ? CalendarFields(calendar, « "year" »).
            val inputFieldNames = TemporalAOs.calendarFields(calendar, listOf("year"))

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
        @ECMAImpl("10.3.13")
        fun getISOFields(arguments: JSArguments): JSValue {
            // 1. Let monthDay be the this value.
            // 2. Perform ? RequireInternalSlot(monthDay, [[InitializedTemporalMonthDay]]).
            val monthDay = thisPlainMonthDay(arguments.thisValue, "getISOFields")

            // 3. Let fields be OrdinaryObjectCreate(%Object.prototype%).
            val fields = JSObject.create()

            // 4. Perform ! CreateDataPropertyOrThrow(fields, "calendar", monthDay.[[Calendar]]).
            AOs.createDataPropertyOrThrow(fields, "calendar".key(), monthDay[Slot.Calendar])

            // 5. Perform ! CreateDataPropertyOrThrow(fields, "isoDay", 𝔽(monthDay.[[ISODay]])).
            AOs.createDataPropertyOrThrow(fields, "isoDay".key(), monthDay[Slot.ISODay].toValue())

            // 6. Perform ! CreateDataPropertyOrThrow(fields, "isoMonth", 𝔽(monthDay.[[ISOMonth]])).
            AOs.createDataPropertyOrThrow(fields, "isoMonth".key(), monthDay[Slot.ISOMonth].toValue())

            // 7. Perform ! CreateDataPropertyOrThrow(fields, "isoYear", 𝔽(monthDay.[[ISOYear]])).
            AOs.createDataPropertyOrThrow(fields, "isoYear".key(), monthDay[Slot.ISOYear].toValue())

            // 8. Return fields.
            return fields
        }
    }
}
