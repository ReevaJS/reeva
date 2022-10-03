package com.reevajs.reeva.runtime.temporal

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.temporal.TemporalAOs
import com.reevajs.reeva.runtime.primitives.JSNumber
import com.reevajs.reeva.runtime.primitives.JSString
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.*
import java.math.BigDecimal
import java.math.BigInteger

class JSDurationProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("constructor", realm.durationCtor)
        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "Temporal.Duration".toValue(), attrs { +conf })

        defineBuiltinGetter("years", ::getYears)
        defineBuiltinGetter("months", ::getMonths)
        defineBuiltinGetter("weeks", ::getWeeks)
        defineBuiltinGetter("days", ::getDays)
        defineBuiltinGetter("hours", ::getHours)
        defineBuiltinGetter("minutes", ::getMinutes)
        defineBuiltinGetter("seconds", ::getSeconds)
        defineBuiltinGetter("milliseonds", ::getMilliseconds)
        defineBuiltinGetter("microseconds", ::getMicroseconds)
        defineBuiltinGetter("nanoseconds", ::getNanoseconds)
        defineBuiltinGetter("sign", ::getSign)
        defineBuiltinGetter("blank", ::getBlank)

        defineBuiltin("with", 1, ::with)
        defineBuiltin("negated", 0, ::negated)
        defineBuiltin("abs", 0, ::abs)
        defineBuiltin("add", 1, ::add)
        defineBuiltin("subtract", 1, ::subtract)
        defineBuiltin("round", 1, ::round)
        defineBuiltin("total", 1, ::total)
        defineBuiltin("toString", 0, ::toString)
        defineBuiltin("toJSON", 0, ::toJSON)
        defineBuiltin("toLocaleString", 0, ::toLocaleString)
        defineBuiltin("valueOf", 0, ::valueOf)
    }

    companion object {
        fun create(realm: Realm) = JSDurationProto(realm).initialize()

        private fun thisDuration(thisValue: JSValue, method: String): JSObject {
            if (!AOs.requireInternalSlot(thisValue, Slot.InitializedTemporalDuration))
                Errors.IncompatibleMethodCall("Duration.prototype.$method").throwTypeError()
            return thisValue
        }

        @JvmStatic
        @ECMAImpl("7.3.3")
        fun getYears(arguments: JSArguments): JSValue {
            // 1. Let duration be the this value.
            // 2. Perform ? RequireInternalSlot(duration, [[InitializedTemporalDuration]]).
            // 3. Return 𝔽(duration.[[Years]]).
            return thisDuration(arguments.thisValue, "get years")[Slot.Years].toValue()
        }

        @JvmStatic
        @ECMAImpl("7.3.4")
        fun getMonths(arguments: JSArguments): JSValue {
            // 1. Let duration be the this value.
            // 2. Perform ? RequireInternalSlot(duration, [[InitializedTemporalDuration]]).
            // 3. Return 𝔽(duration.[[Months]]).
            return thisDuration(arguments.thisValue, "get months")[Slot.Months].toValue()
        }

        @JvmStatic
        @ECMAImpl("7.3.5")
        fun getWeeks(arguments: JSArguments): JSValue {
            // 1. Let duration be the this value.
            // 2. Perform ? RequireInternalSlot(duration, [[InitializedTemporalDuration]]).
            // 3. Return 𝔽(duration.[[Weeks]]).
            return thisDuration(arguments.thisValue, "get weeks")[Slot.Weeks].toValue()
        }

        @JvmStatic
        @ECMAImpl("7.3.6")
        fun getDays(arguments: JSArguments): JSValue {
            // 1. Let duration be the this value.
            // 2. Perform ? RequireInternalSlot(duration, [[InitializedTemporalDuration]]).
            // 3. Return 𝔽(duration.[[Days]]).
            return thisDuration(arguments.thisValue, "get days")[Slot.Days].toValue()
        }

        @JvmStatic
        @ECMAImpl("7.3.7")
        fun getHours(arguments: JSArguments): JSValue {
            // 1. Let duration be the this value.
            // 2. Perform ? RequireInternalSlot(duration, [[InitializedTemporalDuration]]).
            // 3. Return 𝔽(duration.[[Hours]]).
            return thisDuration(arguments.thisValue, "get hours")[Slot.Hours].toValue()
        }

        @JvmStatic
        @ECMAImpl("7.3.8")
        fun getMinutes(arguments: JSArguments): JSValue {
            // 1. Let duration be the this value.
            // 2. Perform ? RequireInternalSlot(duration, [[InitializedTemporalDuration]]).
            // 3. Return 𝔽(duration.[[Minutes]]).
            return thisDuration(arguments.thisValue, "get minutes")[Slot.Minutes].toValue()
        }

        @JvmStatic
        @ECMAImpl("7.3.9")
        fun getSeconds(arguments: JSArguments): JSValue {
            // 1. Let duration be the this value.
            // 2. Perform ? RequireInternalSlot(duration, [[InitializedTemporalDuration]]).
            // 3. Return 𝔽(duration.[[Seconds]]).
            return thisDuration(arguments.thisValue, "get seconds")[Slot.Seconds].toValue()
        }

        @JvmStatic
        @ECMAImpl("7.3.10")
        fun getMilliseconds(arguments: JSArguments): JSValue {
            // 1. Let duration be the this value.
            // 2. Perform ? RequireInternalSlot(duration, [[InitializedTemporalDuration]]).
            // 3. Return 𝔽(duration.[[Millseconds]]).
            return thisDuration(arguments.thisValue, "get milliseconds")[Slot.Milliseconds].toValue()
        }

        @JvmStatic
        @ECMAImpl("7.3.11")
        fun getMicroseconds(arguments: JSArguments): JSValue {
            // 1. Let duration be the this value.
            // 2. Perform ? RequireInternalSlot(duration, [[InitializedTemporalDuration]]).
            // 3. Return 𝔽(duration.[[Microseconds]]).
            return thisDuration(arguments.thisValue, "get microseconds")[Slot.Microseconds].toValue()
        }

        @JvmStatic
        @ECMAImpl("7.3.12")
        fun getNanoseconds(arguments: JSArguments): JSValue {
            // 1. Let duration be the this value.
            // 2. Perform ? RequireInternalSlot(duration, [[InitializedTemporalDuration]]).
            // 3. Return 𝔽(duration.[[Nanoseconds]]).
            return thisDuration(arguments.thisValue, "get nanoseconds")[Slot.Nanoseconds].toValue()
        }

        @JvmStatic
        @ECMAImpl("7.3.13")
        fun getSign(arguments: JSArguments): JSValue {
            // 1. Let duration be the this value.
            // 2. Perform ? RequireInternalSlot(duration, [[InitializedTemporalDuration]]).
            val duration = thisDuration(arguments.thisValue, "get sign")

            // 3. Return 𝔽(! DurationSign(duration.[[Years]], duration.[[Months]], duration.[[Weeks]], duration.[[Days]], duration.[[Hours]], duration.[[Minutes]], duration.[[Seconds]], duration.[[Milliseconds]], duration.[[Microseconds]], duration.[[Nanoseconds]])).
            return TemporalAOs.durationSign(TemporalAOs.DurationRecord(
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
            )).toValue()
        }

        @JvmStatic
        @ECMAImpl("7.3.14")
        fun getBlank(arguments: JSArguments): JSValue {
            // 1. Let duration be the this value.
            // 2. Perform ? RequireInternalSlot(duration, [[InitializedTemporalDuration]]).
            // 3. Let sign be ! DurationSign(duration.[[Years]], duration.[[Months]], duration.[[Weeks]], duration.[[Days]], duration.[[Hours]], duration.[[Minutes]], duration.[[Seconds]], duration.[[Milliseconds]], duration.[[Microseconds]], duration.[[Nanoseconds]]).
            val sign = getSign(arguments) as JSNumber

            // 4. If sign = 0, return true.
            // 5. Return false.
            return (sign.number == 0.0).toValue()
        }

        @JvmStatic
        @ECMAImpl("7.3.15")
        fun with(arguments: JSArguments): JSValue {
            // 1. Let duration be the this value.
            // 2. Perform ? RequireInternalSlot(duration, [[InitializedTemporalDuration]]).
            val duration = thisDuration(arguments.thisValue, "with")

            // 3. Let temporalDurationLike be ? ToTemporalPartialDurationRecord(temporalDurationLike).
            val temporalDurationLike = TemporalAOs.toTemporalPartialDurationRecord(arguments.argument(0))

            // 4. If temporalDurationLike.[[Years]] is not undefined, then
            //    a. Let years be temporalDurationLike.[[Years]].
            // 5. Else,
            //    a. Let years be duration.[[Years]].
            val years = temporalDurationLike.years ?: duration[Slot.Years]

            // 6. If temporalDurationLike.[[Months]] is not undefined, then
            //    a. Let months be temporalDurationLike.[[Months]].
            // 7. Else,
            //    a. Let months be duration.[[Months]].
            val months = temporalDurationLike.months ?: duration[Slot.Months]

            // 8. If temporalDurationLike.[[Weeks]] is not undefined, then
            //    a. Let weeks be temporalDurationLike.[[Weeks]].
            // 9. Else,
            //    a. Let weeks be duration.[[Weeks]].
            val weeks = temporalDurationLike.weeks ?: duration[Slot.Weeks]

            // 10. If temporalDurationLike.[[Days]] is not undefined, then
            //     a. Let days be temporalDurationLike.[[Days]].
            // 11. Else,
            //     a. Let days be duration.[[Days]].
            val days = temporalDurationLike.days ?: duration[Slot.Days]

            // 12. If temporalDurationLike.[[Hours]] is not undefined, then
            //     a. Let hours be temporalDurationLike.[[Hours]].
            // 13. Else,
            //     a. Let hours be duration.[[Hours]].
            val hours = temporalDurationLike.hours ?: duration[Slot.Hours]

            // 14. If temporalDurationLike.[[Minutes]] is not undefined, then
            //     a. Let minutes be temporalDurationLike.[[Minutes]].
            // 15. Else,
            //     a. Let minutes be duration.[[Minutes]].
            val minutes = temporalDurationLike.minutes ?: duration[Slot.Minutes]
            
            // 16. If temporalDurationLike.[[Seconds]] is not undefined, then
            //     a. Let seconds be temporalDurationLike.[[Seconds]].
            // 17. Else,
            //     a. Let seconds be duration.[[Seconds]].
            val seconds = temporalDurationLike.seconds ?: duration[Slot.Seconds]

            // 18. If temporalDurationLike.[[Milliseconds]] is not undefined, then
            //     a. Let milliseconds be temporalDurationLike.[[Milliseconds]].
            // 19. Else,
            //     a. Let milliseconds be duration.[[Milliseconds]].
            val milliseconds = temporalDurationLike.milliseconds ?: duration[Slot.Milliseconds]

            // 20. If temporalDurationLike.[[Microseconds]] is not undefined, then
            //     a. Let microseconds be temporalDurationLike.[[Microseconds]].
            // 21. Else,
            //     a. Let microseconds be duration.[[Microseconds]].
            val microseconds = temporalDurationLike.microseconds ?: duration[Slot.Microseconds]

            // 22. If temporalDurationLike.[[Nanoseconds]] is not undefined, then
            //     a. Let nanoseconds be temporalDurationLike.[[Nanoseconds]].
            // 23. Else,
            //     a. Let nanoseconds be duration.[[Nanoseconds]].
            val nanoseconds = temporalDurationLike.nanoseconds ?: duration[Slot.Nanoseconds]

            // 24. Return ? CreateTemporalDuration(years, months, weeks, days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds).
            return TemporalAOs.createTemporalDuration(TemporalAOs.DurationRecord(
                years, months, weeks, days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds,
            ))
        }

        @JvmStatic
        @ECMAImpl("7.3.16")
        fun negated(arguments: JSArguments): JSValue {
            // 1. Let duration be the this value.
            // 2. Perform ? RequireInternalSlot(duration, [[InitializedTemporalDuration]]).
            val duration = thisDuration(arguments.thisValue, "negated")

            // 3. Return ! CreateNegatedTemporalDuration(duration).
            return TemporalAOs.createNegatedTemporalDuration(duration)
        }

        @JvmStatic
        @ECMAImpl("7.3.17")
        fun abs(arguments: JSArguments): JSValue {
            // 1. Let duration be the this value.
            // 2. Perform ? RequireInternalSlot(duration, [[InitializedTemporalDuration]]).
            val duration = thisDuration(arguments.thisValue, "abs")

            // 3. Return ! CreateTemporalDuration(abs(duration.[[Years]]), abs(duration.[[Months]]), abs(duration.[[Weeks]]), abs(duration.[[Days]]), abs(duration.[[Hours]]), abs(duration.[[Minutes]]), abs(duration.[[Seconds]]), abs(duration.[[Milliseconds]]), abs(duration.[[Microseconds]]), abs(duration.[[Nanoseconds]])).
            return TemporalAOs.createTemporalDuration(TemporalAOs.DurationRecord(
                kotlin.math.abs(duration[Slot.Years]),
                kotlin.math.abs(duration[Slot.Months]),
                kotlin.math.abs(duration[Slot.Weeks]),
                kotlin.math.abs(duration[Slot.Days]),
                kotlin.math.abs(duration[Slot.Hours]),
                kotlin.math.abs(duration[Slot.Minutes]),
                kotlin.math.abs(duration[Slot.Seconds]),
                kotlin.math.abs(duration[Slot.Milliseconds]),
                kotlin.math.abs(duration[Slot.Microseconds]),
                duration[Slot.Nanoseconds].abs(),
            ))
        }

        @JvmStatic
        @ECMAImpl("7.3.18")
        fun add(arguments: JSArguments): JSValue {
            // 1. Let duration be the this value.
            // 2. Perform ? RequireInternalSlot(duration, [[InitializedTemporalDuration]]).
            val duration = thisDuration(arguments.thisValue, "add")

            // 3. Return ? AddDurationToOrSubtractDurationFromDuration(add, duration, other, options).
            return TemporalAOs.addDurationToOrSubtractDurationFromDuration(true, duration, arguments.argument(0), arguments.argument(1))
        }

        @JvmStatic
        @ECMAImpl("7.3.19")
        fun subtract(arguments: JSArguments): JSValue {
            // 1. Let duration be the this value.
            // 2. Perform ? RequireInternalSlot(duration, [[InitializedTemporalDuration]]).
            val duration = thisDuration(arguments.thisValue, "subtract")

            // 3. Return ? AddDurationToOrSubtractDurationFromDuration(subtract, duration, other, options).
            return TemporalAOs.addDurationToOrSubtractDurationFromDuration(false, duration, arguments.argument(0), arguments.argument(1))
        }

        @JvmStatic
        @ECMAImpl("7.3.20")
        fun round(arguments: JSArguments): JSValue {
            var roundTo = arguments.argument(0)

            // 1. Let duration be the this value.
            // 2. Perform ? RequireInternalSlot(duration, [[InitializedTemporalDuration]]).
            val duration = thisDuration(arguments.thisValue, "round")

            // 3. If roundTo is undefined, then
            if (roundTo == JSUndefined) {
                // a. Throw a TypeError exception.
                Errors.TODO("round 1").throwTypeError()
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

            // 6. Let smallestUnitPresent be true.
            var smallestUnitPresent = true

            // 7. Let largestUnitPresent be true.
            var largestUnitPresent = true

            // 8. Let smallestUnit be ? GetTemporalUnit(roundTo, "smallestUnit", datetime, undefined).
            var smallestUnit = TemporalAOs.getTemporalUnit(roundTo, "smallestUnit".key(), "datetime", TemporalAOs.TemporalUnitDefault.Value(JSUndefined))

            // 9. If smallestUnit is undefined, then
            if (smallestUnit == null) {
                // a. Set smallestUnitPresent to false.
                smallestUnitPresent = false

                // b. Set smallestUnit to "nanosecond".
                smallestUnit = "nanosecond"
            }

            // 10. Let defaultLargestUnit be ! DefaultTemporalLargestUnit(duration.[[Years]], duration.[[Months]], duration.[[Weeks]], duration.[[Days]], duration.[[Hours]], duration.[[Minutes]], duration.[[Seconds]], duration.[[Milliseconds]], duration.[[Microseconds]]).
            var defaultLargestUnit = TemporalAOs.defaultTemporalLargestUnit(duration[Slot.Years], duration[Slot.Months], duration[Slot.Weeks], duration[Slot.Days], duration[Slot.Hours], duration[Slot.Minutes], duration[Slot.Seconds], duration[Slot.Milliseconds], duration[Slot.Microseconds])

            // 11. Set defaultLargestUnit to ! LargerOfTwoTemporalUnits(defaultLargestUnit, smallestUnit).
            defaultLargestUnit = TemporalAOs.largerOfTwoTemporalUnits(defaultLargestUnit, smallestUnit)

            // 12. Let largestUnit be ? GetTemporalUnit(roundTo, "largestUnit", datetime, undefined, « "auto" »).
            var largestUnit = TemporalAOs.getTemporalUnit(roundTo, "largestUnit".key(), "datetime", TemporalAOs.TemporalUnitDefault.Value(JSUndefined), listOf("auto"))

            // 13. If largestUnit is undefined, then
            if (largestUnit == null) {
                // a. Set largestUnitPresent to false.
                largestUnitPresent = false

                // b. Set largestUnit to defaultLargestUnit.
                largestUnit = defaultLargestUnit
            }
            // 14. Else if largestUnit is "auto", then
            else if (largestUnit == "auto") {
                // a. Set largestUnit to defaultLargestUnit.
                largestUnit = defaultLargestUnit
            }

            // 15. If smallestUnitPresent is false and largestUnitPresent is false, then
            if (!smallestUnitPresent && !largestUnitPresent) {
                // a. Throw a RangeError exception.
                Errors.TODO("round 2").throwRangeError()
            }

            // 16. If LargerOfTwoTemporalUnits(largestUnit, smallestUnit) is not largestUnit, throw a RangeError exception.
            if (TemporalAOs.largerOfTwoTemporalUnits(largestUnit, smallestUnit) != largestUnit)
                Errors.TODO("round 3").throwRangeError()

            // 17. Let roundingMode be ? ToTemporalRoundingMode(roundTo, "halfExpand").
            val roundingMode = TemporalAOs.toTemporalRoundingMode(roundTo, "halfExpand")

            // 18. Let maximum be ! MaximumTemporalDurationRoundingIncrement(smallestUnit).
            val maximum = TemporalAOs.maximumTemporalDurationRoundingIncrement(smallestUnit)

            // 19. Let roundingIncrement be ? ToTemporalRoundingIncrement(roundTo, maximum, false).
            val roundingIncrement = TemporalAOs.toTemporalRoundingIncrement(roundTo, maximum, false)

            // 20. Let relativeTo be ? ToRelativeTemporalObject(roundTo).
            var relativeTo = TemporalAOs.toRelativeTemporalObject(roundTo)

            // 21. Let unbalanceResult be ? UnbalanceDurationRelative(duration.[[Years]], duration.[[Months]], duration.[[Weeks]], duration.[[Days]], largestUnit, relativeTo).
            val unbalanceResult = TemporalAOs.unbalanceDurationRelative(duration[Slot.Years], duration[Slot.Months], duration[Slot.Weeks], duration[Slot.Days], largestUnit, relativeTo)

            // 22. Let roundResult be (? RoundDuration(unbalanceResult.[[Years]], unbalanceResult.[[Months]], unbalanceResult.[[Weeks]], unbalanceResult.[[Days]], duration.[[Hours]], duration.[[Minutes]], duration.[[Seconds]], duration.[[Milliseconds]], duration.[[Microseconds]], duration.[[Nanoseconds]], roundingIncrement, smallestUnit, roundingMode, relativeTo)).[[DurationRecord]].
            val roundResult = TemporalAOs.roundDuration(
                TemporalAOs.DurationRecord(
                    unbalanceResult.years, 
                    unbalanceResult.months, 
                    unbalanceResult.weeks, 
                    unbalanceResult.days, 
                    duration[Slot.Hours], 
                    duration[Slot.Minutes], 
                    duration[Slot.Seconds], 
                    duration[Slot.Milliseconds], 
                    duration[Slot.Microseconds], 
                    duration[Slot.Nanoseconds], 
                ),
                roundingIncrement, smallestUnit, roundingMode, relativeTo,
            ).duration

            // 23. Let adjustResult be ? AdjustRoundedDurationDays(roundResult.[[Years]], roundResult.[[Months]], roundResult.[[Weeks]], roundResult.[[Days]], roundResult.[[Hours]], roundResult.[[Minutes]], roundResult.[[Seconds]], roundResult.[[Milliseconds]], roundResult.[[Microseconds]], roundResult.[[Nanoseconds]], roundingIncrement, smallestUnit, roundingMode, relativeTo).
            val adjustResult = TemporalAOs.adjustRoundedDurationDays(roundResult, roundingIncrement, smallestUnit, roundingMode, relativeTo)

            // 24. Let balanceResult be ? BalanceDurationRelative(adjustResult.[[Years]], adjustResult.[[Months]], adjustResult.[[Weeks]], adjustResult.[[Days]], largestUnit, relativeTo).
            val balanceResult = TemporalAOs.balanceDurationRelative(adjustResult.years, adjustResult.months, adjustResult.weeks, adjustResult.days, largestUnit, relativeTo)

            // 25. If Type(relativeTo) is Object and relativeTo has an [[InitializedTemporalZonedDateTime]] internal slot, then
            if (relativeTo is JSObject && Slot.InitializedTemporalZonedDateTime in relativeTo) {
                // a. Set relativeTo to ? MoveRelativeZonedDateTime(relativeTo, balanceResult.[[Years]], balanceResult.[[Months]], balanceResult.[[Weeks]], 0).
                relativeTo = TemporalAOs.moveRelativeZonedDateTime(relativeTo, balanceResult.years, balanceResult.months, balanceResult.weeks, 0)
            }

            // 26. Let result be ? BalanceDuration(balanceResult.[[Days]], adjustResult.[[Hours]], adjustResult.[[Minutes]], adjustResult.[[Seconds]], adjustResult.[[Milliseconds]], adjustResult.[[Microseconds]], adjustResult.[[Nanoseconds]], largestUnit, relativeTo).
            val result = TemporalAOs.balanceDuration(balanceResult.days, adjustResult.hours, adjustResult.minutes, adjustResult.seconds, adjustResult.milliseconds, adjustResult.microseconds, adjustResult.nanoseconds, largestUnit, relativeTo)

            // 27. Return ! CreateTemporalDuration(balanceResult.[[Years]], balanceResult.[[Months]], balanceResult.[[Weeks]], result.[[Days]], result.[[Hours]], result.[[Minutes]], result.[[Seconds]], result.[[Milliseconds]], result.[[Microseconds]], result.[[Nanoseconds]]).
            return TemporalAOs.createTemporalDuration(TemporalAOs.DurationRecord(
                balanceResult.years,
                balanceResult.months,
                balanceResult.weeks,
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
        @ECMAImpl("7.3.21")
        fun total(arguments: JSArguments): JSValue {
            var totalOf = arguments.argument(0)

            // 1. Let duration be the this value.
            // 2. Perform ? RequireInternalSlot(duration, [[InitializedTemporalDuration]]).
            val duration = thisDuration(arguments.thisValue, "total")

            // 3. If totalOf is undefined, throw a TypeError exception.
            if (totalOf == JSUndefined)
                Errors.TODO("Duration.prototype.total").throwTypeError()

            // 4. If Type(totalOf) is String, then
            if (totalOf is JSString) {
                // a. Let paramString be totalOf.
                val paramString = totalOf

                // b. Set totalOf to OrdinaryObjectCreate(null).
                totalOf = JSObject.create()

                // c. Perform ! CreateDataPropertyOrThrow(totalOf, "unit", paramString).
                AOs.createDataPropertyOrThrow(totalOf, "unit".key(), paramString)
            }
            // 5. Else,
            else {
                // a. Set totalOf to ? GetOptionsObject(totalOf).
                totalOf = TemporalAOs.getOptionsObject(totalOf)
            }

            // 6. Let relativeTo be ? ToRelativeTemporalObject(totalOf).
            val relativeTo = TemporalAOs.toRelativeTemporalObject(totalOf)

            // 7. Let unit be ? GetTemporalUnit(totalOf, "unit", datetime, required).
            val unit = TemporalAOs.getTemporalUnit(totalOf, "unit".key(), "datetime", TemporalAOs.TemporalUnitDefault.Required)!!

            // 8. Let unbalanceResult be ? UnbalanceDurationRelative(duration.[[Years]], duration.[[Months]], duration.[[Weeks]], duration.[[Days]], unit, relativeTo).
            val unbalanceResult = TemporalAOs.unbalanceDurationRelative(duration[Slot.Years], duration[Slot.Months], duration[Slot.Weeks], duration[Slot.Days], unit, relativeTo)

            // 9. Let intermediate be undefined.
            var intermediate: JSValue = JSUndefined

            // 10. If Type(relativeTo) is Object and relativeTo has an [[InitializedTemporalZonedDateTime]] internal slot, then
            if (relativeTo is JSObject && Slot.InitializedTemporalZonedDateTime in relativeTo) {
                // a. Set intermediate to ? MoveRelativeZonedDateTime(relativeTo, unbalanceResult.[[Years]], unbalanceResult.[[Months]], unbalanceResult.[[Weeks]], 0).
                intermediate = TemporalAOs.moveRelativeZonedDateTime(relativeTo, unbalanceResult.years, unbalanceResult.months, unbalanceResult.weeks, 0)
            }

            // 11. Let balanceResult be ? BalancePossiblyInfiniteDuration(unbalanceResult.[[Days]], duration.[[Hours]], duration.[[Minutes]], duration.[[Seconds]], duration.[[Milliseconds]], duration.[[Microseconds]], duration.[[Nanoseconds]], unit, intermediate).
            val balanceResult = TemporalAOs.balancePossiblyInfiniteDuration(unbalanceResult.days, duration[Slot.Hours], duration[Slot.Minutes], duration[Slot.Seconds], duration[Slot.Milliseconds], duration[Slot.Microseconds], duration[Slot.Nanoseconds], unit, intermediate)

            // 12. If balanceResult is positive overflow, return +∞𝔽.
            if (balanceResult == TemporalAOs.InfiniteDurationResult.PositiveOverflow) 
                return JSNumber.POSITIVE_INFINITY

            // 13. If balanceResult is negative overflow, return -∞𝔽.
            if (balanceResult == TemporalAOs.InfiniteDurationResult.NegativeOverflow)
                return JSNumber.NEGATIVE_INFINITY

            // 14. Assert: balanceResult is a Time Duration Record.
            ecmaAssert(balanceResult is TemporalAOs.InfiniteDurationResult.Balanced)

            // 15. Let roundRecord be ? RoundDuration(unbalanceResult.[[Years]], unbalanceResult.[[Months]], unbalanceResult.[[Weeks]], balanceResult.[[Days]], balanceResult.[[Hours]], balanceResult.[[Minutes]], balanceResult.[[Seconds]], balanceResult.[[Milliseconds]], balanceResult.[[Microseconds]], balanceResult.[[Nanoseconds]], 1, unit, "trunc", relativeTo).
            val roundRecord = TemporalAOs.roundDuration(
                TemporalAOs.DurationRecord(
                    unbalanceResult.years, 
                    unbalanceResult.months, 
                    unbalanceResult.weeks, 
                    balanceResult.result.days, 
                    balanceResult.result.hours, 
                    balanceResult.result.minutes, 
                    balanceResult.result.seconds, 
                    balanceResult.result.milliseconds, 
                    balanceResult.result.microseconds, 
                    balanceResult.result.nanoseconds,
                ),
                1, 
                unit, 
                "trunc", 
                relativeTo,
            )

            // 16. Let roundResult be roundRecord.[[DurationRecord]].
            val roundResult = roundRecord.duration

            val whole = when (unit) {
                // 17. If unit is "year", then
                //     a. Let whole be roundResult.[[Years]].
                "year" -> roundResult.years.toDouble()

                // 18. Else if unit is "month", then
                //     a. Let whole be roundResult.[[Months]].
                "month" -> roundResult.months.toDouble()

                // 19. Else if unit is "week", then
                //     a. Let whole be roundResult.[[Weeks]].
                "week" -> roundResult.weeks.toDouble()

                // 20. Else if unit is "day", then
                //     a. Let whole be roundResult.[[Days]].
                "day" -> roundResult.days.toDouble()

                // 21. Else if unit is "hour", then
                //     a. Let whole be roundResult.[[Hours]].
                "hour" -> roundResult.hours.toDouble()

                // 22. Else if unit is "minute", then
                //     a. Let whole be roundResult.[[Minutes]].
                "minute" -> roundResult.minutes.toDouble()

                // 23. Else if unit is "second", then
                //     a. Let whole be roundResult.[[Seconds]].
                "second" -> roundResult.seconds.toDouble()

                // 24. Else if unit is "millisecond", then
                //     a. Let whole be roundResult.[[Milliseconds]].
                "millisecond" -> roundResult.milliseconds.toDouble()

                // 25. Else if unit is "microsecond", then
                //     a. Let whole be roundResult.[[Microseconds]].
                "microsecond" -> roundResult.microseconds.toDouble()

                // 26. Else,
                //     a. Assert: unit is "nanosecond".
                //     b. Let whole be roundResult.[[Nanoseconds]].
                "nanosecond" -> roundResult.nanoseconds.toBigDecimal().toDouble()
                else -> unreachable()
            }

            // 27. Return 𝔽(whole + roundRecord.[[Remainder]]).
            return JSNumber(whole + roundRecord.remainder.toBigDecimal().toDouble())
        }

        @JvmStatic
        @ECMAImpl("7.3.22")
        fun toString(arguments: JSArguments): JSValue {
            var options = arguments.argument(0)

            // 1. Let duration be the this value.
            // 2. Perform ? RequireInternalSlot(duration, [[InitializedTemporalDuration]]).
            val duration = thisDuration(arguments.thisValue, "toString")

            // 3. Set options to ? GetOptionsObject(options).
            options = TemporalAOs.getOptionsObject(options)

            // 4. Let precision be ? ToSecondsStringPrecision(options).
            val precision = TemporalAOs.toSecondsStringPrecision(options)

            // 5. If precision.[[Unit]] is "minute", throw a RangeError exception.
            if (precision.unit == "minute")
                Errors.TODO("Duration.prototype.toString").throwRangeError()

            // 6. Let roundingMode be ? ToTemporalRoundingMode(options, "trunc").
            val roundingMode = TemporalAOs.toTemporalRoundingMode(options, "trunc")

            // 7. Let result be (? RoundDuration(duration.[[Years]], duration.[[Months]], duration.[[Weeks]], duration.[[Days]], duration.[[Hours]], duration.[[Minutes]], duration.[[Seconds]], duration.[[Milliseconds]], duration.[[Microseconds]], duration.[[Nanoseconds]], precision.[[Increment]], precision.[[Unit]], roundingMode)).[[DurationRecord]].
            val result = TemporalAOs.roundDuration(
                TemporalAOs.DurationRecord(
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
                ),
                precision.increment,
                precision.unit,
                roundingMode,
            ).duration

            // 8. Return ! TemporalDurationToString(result.[[Years]], result.[[Months]], result.[[Weeks]], result.[[Days]], result.[[Hours]], result.[[Minutes]], result.[[Seconds]], result.[[Milliseconds]], result.[[Microseconds]], result.[[Nanoseconds]], precision.[[Precision]]).
            return TemporalAOs.temporalDurationToString(result, precision.precision.let { if (it == "auto") null else it as Int }).toValue()
        }

        @JvmStatic
        @ECMAImpl("7.3.23")
        fun toJSON(arguments: JSArguments): JSValue {
            // 1. Let duration be the this value.
            // 2. Perform ? RequireInternalSlot(duration, [[InitializedTemporalDuration]]).
            val duration = thisDuration(arguments.thisValue, "toJSON")
    
            // 3. Return ! TemporalDurationToString(duration.[[Years]], duration.[[Months]], duration.[[Weeks]], duration.[[Days]], duration.[[Hours]], duration.[[Minutes]], duration.[[Seconds]], duration.[[Milliseconds]], duration.[[Microseconds]], duration.[[Nanoseconds]], "auto").
            return TemporalAOs.temporalDurationToString(
                TemporalAOs.DurationRecord(
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
                ),
                null,
            ).toValue()
        }
    
        @JvmStatic
        @ECMAImpl("7.3.24")
        fun toLocaleString(arguments: JSArguments): JSValue {
            // 1. Let duration be the this value.
            // 2. Perform ? RequireInternalSlot(duration, [[InitializedTemporalDuration]]).
            val duration = thisDuration(arguments.thisValue, "toLocaleString")
    
            // 3. Return ! TemporalDurationToString(duration.[[Years]], duration.[[Months]], duration.[[Weeks]], duration.[[Days]], duration.[[Hours]], duration.[[Minutes]], duration.[[Seconds]], duration.[[Milliseconds]], duration.[[Microseconds]], duration.[[Nanoseconds]], "auto").
            return TemporalAOs.temporalDurationToString(
                TemporalAOs.DurationRecord(
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
                ),
                null,
            ).toValue()
        }
    
        @JvmStatic
        @ECMAImpl("7.3.25")
        fun valueOf(arguments: JSArguments): JSValue {
            // 1. Throw a TypeError exception.
            Errors.TODO("Duration.prototype.valueOf").throwTypeError()
        }
    }
}
