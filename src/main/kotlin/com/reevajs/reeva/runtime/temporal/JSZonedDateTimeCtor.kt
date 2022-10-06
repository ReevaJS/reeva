package com.reevajs.reeva.runtime.temporal

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.temporal.TemporalAOs
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.toBigInt
import com.reevajs.reeva.utils.*

class JSZonedDateTimeCtor private constructor(realm: Realm) : JSNativeFunction(realm, "ZonedDateTime", 2) {
    override fun init() {
        super.init()

        defineBuiltin("from", 1, ::from)
        defineBuiltin("compare", 2, ::compare)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        // 1. If NewTarget is undefined, then
        if (arguments.newTarget == JSUndefined) {
            // a. Throw a TypeError exception.
            Errors.CtorCallWithoutNew("ZonedDateTime").throwTypeError(realm)
        }

        // 2. Set epochNanoseconds to ? ToBigInt(epochNanoseconds).
        val epochNanoseconds = arguments.argument(0).toBigInt().number

        // 3. If ! IsValidEpochNanoseconds(epochNanoseconds) is false, throw a RangeError exception.
        if (!TemporalAOs.isValidEpochNanoseconds(epochNanoseconds))
            Errors.Temporal.InvalidEpochNanoseconds(epochNanoseconds.toString()).throwRangeError()

        // 4. Let timeZone be ? ToTemporalTimeZone(timeZoneLike).
        val timeZone = TemporalAOs.toTemporalTimeZone(arguments.argument(1))

        // 5. Let calendar be ? ToTemporalCalendarWithISODefault(calendarLike).
        val calendar = TemporalAOs.toTemporalCalendarWithISODefault(arguments.argument(2))

        // 6. Return ? CreateTemporalZonedDateTime(epochNanoseconds, timeZone, calendar, NewTarget).
        return TemporalAOs.createTemporalZonedDateTime(epochNanoseconds, timeZone, calendar, arguments.newTarget as JSObject)
    }

    companion object {
        @JvmStatic
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSZonedDateTimeCtor(realm).initialize()

        @JvmStatic
        @ECMAImpl("6.2.2")
        fun from(arguments: JSArguments): JSValue {
            val item = arguments.argument(0)

            // 1. Set options to ? GetOptionsObject(options).
            val options = TemporalAOs.getOptionsObject(arguments.argument(1))

            // 2. If Type(item) is Object and item has an [[InitializedTemporalZonedDateTime]] internal slot, then
            if (item is JSObject && Slot.InitializedTemporalZonedDateTime in item) {
                // a. Perform ? ToTemporalOverflow(options).
                TemporalAOs.toTemporalOverflow(options)

                // b. Perform ? ToTemporalDisambiguation(options).
                TemporalAOs.toTemporalDisambiguation(options)

                // c. Perform ? ToTemporalOffset(options, "reject").
                TemporalAOs.toTemporalOffset(options, "reject")

                // d. Return ! CreateTemporalZonedDateTime(item.[[Nanoseconds]], item.[[TimeZone]], item.[[Calendar]]).
                return TemporalAOs.createTemporalZonedDateTime(item[Slot.Nanoseconds], item[Slot.TimeZone], item[Slot.Calendar])
            }

            // 3. Return ? ToTemporalZonedDateTime(item, options).
            return TemporalAOs.toTemporalZonedDateTime(item, options)
        }

        @JvmStatic
        @ECMAImpl("6.2.3")
        fun compare(arguments: JSArguments): JSValue {
            // 1. Set one to ? ToTemporalZonedDateTime(one).
            val one = TemporalAOs.toTemporalZonedDateTime(arguments.argument(0))
            
            // 2. Set two to ? ToTemporalZonedDateTime(two).
            val two = TemporalAOs.toTemporalZonedDateTime(arguments.argument(1))

            // 3. Return 𝔽(! CompareEpochNanoseconds(one.[[Nanoseconds]], two.[[Nanoseconds]])).
            return TemporalAOs.compareEpochNanoseconds(one[Slot.Nanoseconds], two[Slot.Nanoseconds]).toValue()
        }
    }
}
