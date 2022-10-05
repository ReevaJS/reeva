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
import com.reevajs.reeva.runtime.primitives.JSTrue
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.temporal.TemporalAOs
import com.reevajs.reeva.utils.*
import java.math.BigInteger

class JSPlainTimeProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("constructor", realm.plainDateCtor)
        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "Temporal.PlainTime".toValue(), attrs { +conf })

        defineBuiltinGetter("calendar", ::getCalendar)
        defineBuiltinGetter("hour", ::getHour)
        defineBuiltinGetter("minute", ::getMinute)
        defineBuiltinGetter("second", ::getSecond)
        defineBuiltinGetter("millisecond", ::getMillisecond)
        defineBuiltinGetter("microsecond", ::getMicrosecond)
        defineBuiltinGetter("nanosecond", ::getNanosecond)

        defineBuiltin("add", 1, ::add)
        defineBuiltin("subtract", 1, ::subtract)
        defineBuiltin("with", 1, ::with)
        defineBuiltin("until", 1, ::until)
        defineBuiltin("since", 1, ::since)
        defineBuiltin("round", 1, ::round)
        defineBuiltin("equals", 1, ::equals)
        defineBuiltin("toPlainDateTime", 1, ::toPlainDateTime)
        defineBuiltin("toZonedDateTime", 1, ::toZonedDateTime)
        defineBuiltin("getISOFields", 0, ::getISOFields)
        defineBuiltin("toString", 0, ::toString)
        defineBuiltin("toLocaleString", 0, ::toLocaleString)
        defineBuiltin("toJSON", 0, ::toJSON)
        defineBuiltin("valueOf", 0, ::valueOf)
    }

    companion object {
        @JvmStatic
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSPlainTimeProto(realm).initialize()

        private fun thisPlainTime(thisValue: JSValue, method: String): JSObject {
            if (!AOs.requireInternalSlot(thisValue, Slot.InitializedTemporalTime))
                Errors.IncompatibleMethodCall("PlainTime.prototype.$method").throwTypeError()
            return thisValue
        }

        @JvmStatic
        @ECMAImpl("4.3.3")
        fun getCalendar(arguments: JSArguments): JSValue {
            // 1. Let temporalTime be the this value.
            // 2. Perform ? RequireInternalSlot(temporalTime, [[InitializedTemporalTime]]).
            // 3. Return temporalTime.[[Calendar]].
            return thisPlainTime(arguments.thisValue, "get calendar")[Slot.Calendar].toValue()
        }

        @JvmStatic
        @ECMAImpl("4.3.4")
        fun getHour(arguments: JSArguments): JSValue {
            // 1. Let temporalTime be the this value.
            // 2. Perform ? RequireInternalSlot(temporalTime, [[InitializedTemporalTime]]).
            // 3. Return 𝔽(temporalTime.[[ISOHour]]).
            return thisPlainTime(arguments.thisValue, "get hour")[Slot.ISOHour].toValue()
        }

        @JvmStatic
        @ECMAImpl("4.3.5")
        fun getMinute(arguments: JSArguments): JSValue {
            // 1. Let temporalTime be the this value.
            // 2. Perform ? RequireInternalSlot(temporalTime, [[InitializedTemporalTime]]).
            // 3. Return 𝔽(temporalTime.[[ISOMinute]]).
            return thisPlainTime(arguments.thisValue, "get minute")[Slot.ISOMinute].toValue()
        }

        @JvmStatic
        @ECMAImpl("4.3.6")
        fun getSecond(arguments: JSArguments): JSValue {
            // 1. Let temporalTime be the this value.
            // 2. Perform ? RequireInternalSlot(temporalTime, [[InitializedTemporalTime]]).
            // 3. Return 𝔽(temporalTime.[[ISOSecond]]).
            return thisPlainTime(arguments.thisValue, "get second")[Slot.ISOSecond].toValue()
        }

        @JvmStatic
        @ECMAImpl("4.3.7")
        fun getMillisecond(arguments: JSArguments): JSValue {
            // 1. Let temporalTime be the this value.
            // 2. Perform ? RequireInternalSlot(temporalTime, [[InitializedTemporalTime]]).
            // 3. Return 𝔽(temporalTime.[[ISOMillisecond]]).
            return thisPlainTime(arguments.thisValue, "get millisecond")[Slot.ISOMillisecond].toValue()
        }

        @JvmStatic
        @ECMAImpl("4.3.8")
        fun getMicrosecond(arguments: JSArguments): JSValue {
            // 1. Let temporalTime be the this value.
            // 2. Perform ? RequireInternalSlot(temporalTime, [[InitializedTemporalTime]]).
            // 3. Return 𝔽(temporalTime.[[ISOMicrosecond]]).
            return thisPlainTime(arguments.thisValue, "get microsecond")[Slot.Microsecond].toValue()
        }

        @JvmStatic
        @ECMAImpl("4.3.9")
        fun getNanosecond(arguments: JSArguments): JSValue {
            // 1. Let temporalTime be the this value.
            // 2. Perform ? RequireInternalSlot(temporalTime, [[InitializedTemporalTime]]).
            // 3. Return 𝔽(temporalTime.[[ISONanosecond]]).
            return thisPlainTime(arguments.thisValue, "get nanosecond")[Slot.Nanosecond].toValue()
        }

        @JvmStatic
        @ECMAImpl("4.3.10")
        fun add(arguments: JSArguments): JSValue {
            // 1. Let temporalTime be the this value.
            // 2. Perform ? RequireInternalSlot(temporalTime, [[InitializedTemporalTime]]).
            val temporalTime = thisPlainTime(arguments.thisValue, "add")

            // 3. Return ? AddDurationToOrSubtractDurationFromPlainTime(add, temporalTime, temporalDurationLike).
            return TemporalAOs.addDurationToOrSubtractDurationFromPlainTime(true, temporalTime, arguments.argument(0))
        }

        @JvmStatic
        @ECMAImpl("4.3.11")
        fun subtract(arguments: JSArguments): JSValue {
            // 1. Let temporalTime be the this value.
            // 2. Perform ? RequireInternalSlot(temporalTime, [[InitializedTemporalTime]]).
            val temporalTime = thisPlainTime(arguments.thisValue, "subtract")

            // 3. Return ? AddDurationToOrSubtractDurationFromPlainTime(subtract, temporalTime, temporalDurationLike).
            return TemporalAOs.addDurationToOrSubtractDurationFromPlainTime(false, temporalTime, arguments.argument(0))
        }

        @JvmStatic
        @ECMAImpl("4.3.12")
        fun with(arguments: JSArguments): JSValue {
            val temporalTimeLike = arguments.argument(0)
            var options = arguments.argument(1)

            // 1. Let temporalTime be the this value.
            // 2. Perform ? RequireInternalSlot(temporalTime, [[InitializedTemporalTime]]).
            val temporalTime = thisPlainTime(arguments.thisValue, "with")

            // 3. If Type(temporalTimeLike) is not Object, then
            if (temporalTimeLike !is JSObject) {
                // a. Throw a TypeError exception.
                Errors.TODO("PlainTime.prototype.with").throwTypeError()
            }

            // 4. Perform ? RejectObjectWithCalendarOrTimeZone(temporalTimeLike).
            TemporalAOs.rejectObjectWithCalendarOrTimeZone(temporalTimeLike)

            // 5. Let partialTime be ? ToTemporalTimeRecord(temporalTimeLike, partial).
            val partialTime = TemporalAOs.toTemporalTimeRecord(temporalTimeLike, "partial")

            // 6. Set options to ? GetOptionsObject(options).
            options = TemporalAOs.getOptionsObject(options)

            // 7. Let overflow be ? ToTemporalOverflow(options).
            val overflow = TemporalAOs.toTemporalOverflow(options)
            
            // 8. If partialTime.[[Hour]] is not undefined, then
            // a. Let hour be partialTime.[[Hour]].
            // 9. Else,
            // a. Let hour be temporalTime.[[ISOHour]].
            val hour = partialTime.hour.takeIf { it != null } ?: temporalTime[Slot.ISOHour]
            
            // 10. If partialTime.[[Minute]] is not undefined, then
            // a. Let minute be partialTime.[[Minute]].
            // 11. Else,
            // a. Let minute be temporalTime.[[ISOMinute]].
            val minute = partialTime.minute.takeIf { it != null } ?: temporalTime[Slot.ISOMinute]

            // 12. If partialTime.[[Second]] is not undefined, then
            // a. Let second be partialTime.[[Second]].
            // 13. Else,
            // a. Let second be temporalTime.[[ISOSecond]].
            val second = partialTime.second.takeIf { it != null } ?: temporalTime[Slot.ISOSecond]

            // 14. If partialTime.[[Millisecond]] is not undefined, then
            // a. Let millisecond be partialTime.[[Millisecond]].
            // 15. Else,
            // a. Let millisecond be temporalTime.[[ISOMillisecond]].
            val millisecond = partialTime.millisecond.takeIf { it != null } ?: temporalTime[Slot.ISOMillisecond]

            // 16. If partialTime.[[Microsecond]] is not undefined, then
            // a. Let microsecond be partialTime.[[Microsecond]].
            // 17. Else,
            // a. Let microsecond be temporalTime.[[ISOMicrosecond]].
            val microsecond = partialTime.microsecond.takeIf { it != null } ?: temporalTime[Slot.ISOMicrosecond]

            // 18. If partialTime.[[Nanosecond]] is not undefined, then
            // a. Let nanosecond be partialTime.[[Nanosecond]].
            // 19. Else,
            // a. Let nanosecond be temporalTime.[[ISONanosecond]].
            val nanosecond = partialTime.nanosecond.takeIf { it != null } ?: temporalTime[Slot.ISONanosecond]

            // 20. Let result be ? RegulateTime(hour, minute, second, millisecond, microsecond, nanosecond, overflow).
            val result = TemporalAOs.regulateTime(hour, minute, second, millisecond, microsecond, nanosecond, overflow)

            // 21. Return ! CreateTemporalTime(result.[[Hour]], result.[[Minute]], result.[[Second]], result.[[Millisecond]], result.[[Microsecond]], result.[[Nanosecond]]).
            return TemporalAOs.createTemporalTime(result.hours, result.minutes, result.seconds, result.milliseconds, result.microseconds, result.nanoseconds)
        }

        @JvmStatic
        @ECMAImpl("4.3.13")
        fun until(arguments: JSArguments): JSValue {
            // 1. Let temporalTime be the this value.
            // 2. Perform ? RequireInternalSlot(temporalTime, [[InitializedTemporalTime]]).
            val temporalTime = thisPlainTime(arguments.thisValue, "until")

            // 3. Return ? DifferenceTemporalPlainTime(until, temporalTime, other, options).
            return TemporalAOs.differenceTemporalPlainTime(true, temporalTime, arguments.argument(0), arguments.argument(1))
        }

        @JvmStatic
        @ECMAImpl("4.3.14")
        fun since(arguments: JSArguments): JSValue {
            // 1. Let temporalTime be the this value.
            // 2. Perform ? RequireInternalSlot(temporalTime, [[InitializedTemporalTime]]).
            val temporalTime = thisPlainTime(arguments.thisValue, "since")

            // 3. Return ? DifferenceTemporalPlainTime(since, temporalTime, other, options).
            return TemporalAOs.differenceTemporalPlainTime(false, temporalTime, arguments.argument(0), arguments.argument(1))
        }

        @JvmStatic
        @ECMAImpl("4.3.15")
        fun round(arguments: JSArguments): JSValue {
            var roundTo = arguments.argument(0)

            // 1. Let temporalTime be the this value.
            // 2. Perform ? RequireInternalSlot(temporalTime, [[InitializedTemporalTime]]).
            val temporalTime = thisPlainTime(arguments.thisValue, "round")

            // 3. If roundTo is undefined, then
            if (roundTo == JSUndefined) {
                // a. Throw a TypeError exception.
                Errors.TODO("PlainTime.prototype.roundTo").throwTypeError()
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

            // 6. Let smallestUnit be ? GetTemporalUnit(roundTo, "smallestUnit", time, required).
            val smallestUnit = TemporalAOs.getTemporalUnit(roundTo, "smallestUnit".key(), "time", TemporalAOs.TemporalUnitDefault.Required)!!

            // 7. Let roundingMode be ? ToTemporalRoundingMode(roundTo, "halfExpand").
            val roundingMode = TemporalAOs.toTemporalRoundingMode(roundTo, "halfExpand")

            // 8. Let maximum be ! MaximumTemporalDurationRoundingIncrement(smallestUnit).
            val maximum = TemporalAOs.maximumTemporalDurationRoundingIncrement(smallestUnit)

            // 9. Let roundingIncrement be ? ToTemporalRoundingIncrement(roundTo, maximum, false).
            val roundingIncrement = TemporalAOs.toTemporalRoundingIncrement(roundTo, maximum, false)

            // 10. Let result be ! RoundTime(temporalTime.[[ISOHour]], temporalTime.[[ISOMinute]], temporalTime.[[ISOSecond]], temporalTime.[[ISOMillisecond]], temporalTime.[[ISOMicrosecond]], temporalTime.[[ISONanosecond]], roundingIncrement, smallestUnit, roundingMode).
            val result = TemporalAOs.roundTime(temporalTime[Slot.ISOHour], temporalTime[Slot.ISOMinute], temporalTime[Slot.ISOSecond], temporalTime[Slot.ISOMillisecond], temporalTime[Slot.ISOMicrosecond], temporalTime[Slot.ISONanosecond], roundingIncrement, smallestUnit, roundingMode)

            // 11. Return ! CreateTemporalTime(result.[[Hour]], result.[[Minute]], result.[[Second]], result.[[Millisecond]], result.[[Microsecond]], result.[[Nanosecond]]).
            return TemporalAOs.createTemporalTime(result.hours, result.minutes, result.seconds, result.milliseconds, result.microseconds, result.nanoseconds)
        }

        @JvmStatic
        @ECMAImpl("4.3.16")
        fun equals(arguments: JSArguments): JSValue {
            // 1. Let temporalTime be the this value.
            // 2. Perform ? RequireInternalSlot(temporalTime, [[InitializedTemporalTime]]).
            val temporalTime = thisPlainTime(arguments.thisValue, "equals")

            // 3. Set other to ? ToTemporalTime(other).
            val other = TemporalAOs.toTemporalTime(arguments.argument(0))

            // 4. If temporalTime.[[ISOHour]] ≠ other.[[ISOHour]], return false.
            if (temporalTime[Slot.ISOHour] != other[Slot.ISOHour])
                return JSFalse

            // 5. If temporalTime.[[ISOMinute]] ≠ other.[[ISOMinute]], return false.
            if (temporalTime[Slot.ISOMinute] != other[Slot.ISOMinute])
                return JSFalse

            // 6. If temporalTime.[[ISOSecond]] ≠ other.[[ISOSecond]], return false.
            if (temporalTime[Slot.ISOSecond] != other[Slot.ISOSecond])
                return JSFalse

            // 7. If temporalTime.[[ISOMillisecond]] ≠ other.[[ISOMillisecond]], return false.
            if (temporalTime[Slot.ISOMillisecond] != other[Slot.ISOMillisecond])
                return JSFalse

            // 8. If temporalTime.[[ISOMicrosecond]] ≠ other.[[ISOMicrosecond]], return false.
            if (temporalTime[Slot.ISOMicrosecond] != other[Slot.ISOMicrosecond])
                return JSFalse

            // 9. If temporalTime.[[ISONanosecond]] ≠ other.[[ISONanosecond]], return false.
            if (temporalTime[Slot.ISONanosecond] != other[Slot.ISONanosecond])
                return JSFalse

            // 10. Return true.
            return JSTrue
        }

        @JvmStatic
        @ECMAImpl("4.3.17")
        fun toPlainDateTime(arguments: JSArguments): JSValue {
            // 1. Let temporalTime be the this value.
            // 2. Perform ? RequireInternalSlot(temporalTime, [[InitializedTemporalTime]]).
            val temporalTime = thisPlainTime(arguments.thisValue, "toPlainDateTime")

            // 3. Set temporalDate to ? ToTemporalDate(temporalDate).
            val temporalDate = TemporalAOs.toTemporalDate(arguments.argument(0))

            // 4. Return ? CreateTemporalDateTime(temporalDate.[[ISOYear]], temporalDate.[[ISOMonth]], temporalDate.[[ISODay]], temporalTime.[[ISOHour]], temporalTime.[[ISOMinute]], temporalTime.[[ISOSecond]], temporalTime.[[ISOMillisecond]], temporalTime.[[ISOMicrosecond]], temporalTime.[[ISONanosecond]], temporalDate.[[Calendar]]).
            return TemporalAOs.createTemporalDateTime(
                temporalDate[Slot.ISOYear],
                temporalDate[Slot.ISOMonth],
                temporalDate[Slot.ISODay],
                temporalTime[Slot.ISOHour],
                temporalTime[Slot.ISOMinute],
                temporalTime[Slot.ISOSecond],
                temporalTime[Slot.ISOMillisecond],
                temporalTime[Slot.ISOMicrosecond],
                temporalTime[Slot.ISONanosecond],
                temporalDate[Slot.Calendar],
            )
        }

        @JvmStatic
        @ECMAImpl("4.3.18")
        fun toZonedDateTime(arguments: JSArguments): JSValue {
            val item = arguments.argument(0)

            // 1. Let temporalTime be the this value.
            // 2. Perform ? RequireInternalSlot(temporalTime, [[InitializedTemporalTime]]).
            val temporalTime = thisPlainTime(arguments.thisValue, "toZonedDateTime")

            // 3. If Type(item) is not Object, then
            if (item !is JSObject) {
                // a. Throw a TypeError exception.
                Errors.TODO("PlainTime.prototype.toZonedDateTime 1").throwTypeError()
            }

            // 4. Let temporalDateLike be ? Get(item, "plainDate").
            val temporalDateLike = item.get("plainDate")

            // 5. If temporalDateLike is undefined, then
            if (temporalDateLike == JSUndefined) {
                // a. Throw a TypeError exception.
                Errors.TODO("PlainTime.prototype.toZonedDateTime 2").throwTypeError()
            }

            // 6. Let temporalDate be ? ToTemporalDate(temporalDateLike).
            val temporalDate = TemporalAOs.toTemporalDate(temporalDateLike)

            // 7. Let temporalTimeZoneLike be ? Get(item, "timeZone").
            val temporalTimeZoneLike = item.get("timeZone")

            // 8. If temporalTimeZoneLike is undefined, then
            if (temporalTimeZoneLike == JSUndefined) {
                // a. Throw a TypeError exception.
                Errors.TODO("PlainTime.prototype.toZonedDateTime 3").throwTypeError()
            }

            // 9. Let timeZone be ? ToTemporalTimeZone(temporalTimeZoneLike).
            val timeZone = TemporalAOs.toTemporalTimeZone(temporalTimeZoneLike)

            // 10. Let temporalDateTime be ? CreateTemporalDateTime(temporalDate.[[ISOYear]], temporalDate.[[ISOMonth]], temporalDate.[[ISODay]], temporalTime.[[ISOHour]], temporalTime.[[ISOMinute]], temporalTime.[[ISOSecond]], temporalTime.[[ISOMillisecond]], temporalTime.[[ISOMicrosecond]], temporalTime.[[ISONanosecond]], temporalDate.[[Calendar]]).
            val temporalDateTime = TemporalAOs.createTemporalDateTime(
                temporalDate[Slot.ISOYear],
                temporalDate[Slot.ISOMonth],
                temporalDate[Slot.ISODay],
                temporalTime[Slot.ISOHour],
                temporalTime[Slot.ISOMinute],
                temporalTime[Slot.ISOSecond],
                temporalTime[Slot.ISOMillisecond],
                temporalTime[Slot.ISOMicrosecond],
                temporalTime[Slot.ISONanosecond],
                temporalDate[Slot.Calendar],
            )

            // 11. Let instant be ? BuiltinTimeZoneGetInstantFor(timeZone, temporalDateTime, "compatible").
            val instant = TemporalAOs.builtinTimeZoneGetInstantFor(timeZone, temporalDateTime, "compatible")

            // 12. Return ! CreateTemporalZonedDateTime(instant.[[Nanoseconds]], timeZone, temporalDate.[[Calendar]]).
            return TemporalAOs.createTemporalZonedDateTime(instant[Slot.Nanoseconds], timeZone, temporalDate[Slot.Calendar])
        }

        @JvmStatic
        @ECMAImpl("4.3.19")
        fun getISOFields(arguments: JSArguments): JSValue {
            // 1. Let temporalTime be the this value.
            // 2. Perform ? RequireInternalSlot(temporalTime, [[InitializedTemporalTime]]).
            val temporalTime = thisPlainTime(arguments.thisValue, "getISOFields")

            // 3. Let fields be OrdinaryObjectCreate(%Object.prototype%).
            val fields = JSObject.create()

            // 4. Perform ! CreateDataPropertyOrThrow(fields, "calendar", temporalTime.[[Calendar]]).
            AOs.createDataPropertyOrThrow(fields, "calendar".key(), temporalTime[Slot.Calendar])

            // 5. Perform ! CreateDataPropertyOrThrow(fields, "isoHour", 𝔽(temporalTime.[[ISOHour]])).
            AOs.createDataPropertyOrThrow(fields, "isoHour".key(), temporalTime[Slot.ISOHour].toValue())

            // 6. Perform ! CreateDataPropertyOrThrow(fields, "isoMicrosecond", 𝔽(temporalTime.[[ISOMicrosecond]])).
            AOs.createDataPropertyOrThrow(fields, "isoMicrosecond".key(), temporalTime[Slot.ISOMicrosecond].toValue())

            // 7. Perform ! CreateDataPropertyOrThrow(fields, "isoMillisecond", 𝔽(temporalTime.[[ISOMillisecond]])).
            AOs.createDataPropertyOrThrow(fields, "isoMillisecond".key(), temporalTime[Slot.ISOMillisecond].toValue())

            // 8. Perform ! CreateDataPropertyOrThrow(fields, "isoMinute", 𝔽(temporalTime.[[ISOMinute]])).
            AOs.createDataPropertyOrThrow(fields, "isoMinute".key(), temporalTime[Slot.ISOMinute].toValue())

            // 9. Perform ! CreateDataPropertyOrThrow(fields, "isoNanosecond", 𝔽(temporalTime.[[ISONanosecond]])).
            AOs.createDataPropertyOrThrow(fields, "isoNanosecond".key(), temporalTime[Slot.ISONanosecond].toValue())

            // 10. Perform ! CreateDataPropertyOrThrow(fields, "isoSecond", 𝔽(temporalTime.[[ISOSecond]])).
            AOs.createDataPropertyOrThrow(fields, "isoSecond".key(), temporalTime[Slot.ISOSecond].toValue())

            // 11. Return fields.
            return fields
        }

        @JvmStatic
        @ECMAImpl("4.3.20")
        fun toString(arguments: JSArguments): JSValue {
            // 1. Let temporalTime be the this value.
            // 2. Perform ? RequireInternalSlot(temporalTime, [[InitializedTemporalTime]]).
            val temporalTime = thisPlainTime(arguments.thisValue, "toString")

            // 3. Set options to ? GetOptionsObject(options).
            val options = TemporalAOs.getOptionsObject(arguments.argument(0))

            // 4. Let precision be ? ToSecondsStringPrecision(options).
            val precision = TemporalAOs.toSecondsStringPrecision(options)

            // 5. Let roundingMode be ? ToTemporalRoundingMode(options, "trunc").
            val roundingMode = TemporalAOs.toTemporalRoundingMode(options, "trunc")

            // 6. Let roundResult be ! RoundTime(temporalTime.[[ISOHour]], temporalTime.[[ISOMinute]], temporalTime.[[ISOSecond]], temporalTime.[[ISOMillisecond]], temporalTime.[[ISOMicrosecond]], temporalTime.[[ISONanosecond]], precision.[[Increment]], precision.[[Unit]], roundingMode).
            val roundResult = TemporalAOs.roundTime(
                temporalTime[Slot.ISOHour],
                temporalTime[Slot.ISOMinute],
                temporalTime[Slot.ISOSecond],
                temporalTime[Slot.ISOMillisecond],
                temporalTime[Slot.ISOMicrosecond],
                temporalTime[Slot.ISONanosecond],
                precision.increment,
                precision.unit,
                roundingMode,
            )

            // 7. Return ! TemporalTimeToString(roundResult.[[Hour]], roundResult.[[Minute]], roundResult.[[Second]], roundResult.[[Millisecond]], roundResult.[[Microsecond]], roundResult.[[Nanosecond]], precision.[[Precision]]).
            return TemporalAOs.temporalTimeToString(
                roundResult.hours,
                roundResult.minutes,
                roundResult.seconds,
                roundResult.milliseconds,
                roundResult.microseconds,
                roundResult.nanoseconds,
                precision.precision as String, // TODO: This cast doesn't seem great
            ).toValue()
        }

        @JvmStatic
        @ECMAImpl("4.3.21")
        fun toLocaleString(arguments: JSArguments): JSValue {
            // 1. Let temporalTime be the this value.
            // 2. Perform ? RequireInternalSlot(temporalTime, [[InitializedTemporalTime]]).
            val temporalTime = thisPlainTime(arguments.thisValue, "toLocaleString")

            // 3. Return ! TemporalTimeToString(temporalTime.[[ISOHour]], temporalTime.[[ISOMinute]], temporalTime.[[ISOSecond]], temporalTime.[[ISOMillisecond]], temporalTime.[[ISOMicrosecond]], temporalTime.[[ISONanosecond]], "auto").
            return TemporalAOs.temporalTimeToString(
                temporalTime[Slot.ISOHour],
                temporalTime[Slot.ISOMinute],
                temporalTime[Slot.ISOSecond],
                temporalTime[Slot.ISOMillisecond],
                temporalTime[Slot.ISOMicrosecond],
                temporalTime[Slot.ISONanosecond],
                "auto",
            ).toValue()
        }

        @JvmStatic
        @ECMAImpl("4.3.22")
        fun toJSON(arguments: JSArguments): JSValue {
            // 1. Let temporalTime be the this value.
            // 2. Perform ? RequireInternalSlot(temporalTime, [[InitializedTemporalTime]]).
            val temporalTime = thisPlainTime(arguments.thisValue, "toJSON")

            // 3. Return ! TemporalTimeToString(temporalTime.[[ISOHour]], temporalTime.[[ISOMinute]], temporalTime.[[ISOSecond]], temporalTime.[[ISOMillisecond]], temporalTime.[[ISOMicrosecond]], temporalTime.[[ISONanosecond]], "auto").
            return TemporalAOs.temporalTimeToString(
                temporalTime[Slot.ISOHour],
                temporalTime[Slot.ISOMinute],
                temporalTime[Slot.ISOSecond],
                temporalTime[Slot.ISOMillisecond],
                temporalTime[Slot.ISOMicrosecond],
                temporalTime[Slot.ISONanosecond],
                "auto",
            ).toValue()
        }

        @JvmStatic
        @ECMAImpl("4.3.23")
        fun valueOf(arguments: JSArguments): JSValue {
            // 1. Throw a TypeError exception.
            Errors.TODO("PlainDate.prototype.valueOf").throwTypeError()
        }
    }
}
