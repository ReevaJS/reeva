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
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue

class JSDurationCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Duration", 1) {
    override fun init() {
        super.init()

        defineBuiltin("from", 1, ::from)
        defineBuiltin("compare", 2, ::compare)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        // 1. If NewTarget is undefined, then
        if (arguments.newTarget == JSUndefined) {
            // a. Throw a TypeError exception.
            Errors.CtorCallWithoutNew("Duration").throwTypeError(realm)
        }

        // 2. Let y be ? ToIntegerWithoutRounding(years).
        val y = TemporalAOs.toIntegerWithoutRounding(arguments.argument(0))

        // 3. Let mo be ? ToIntegerWithoutRounding(months).
        val mo = TemporalAOs.toIntegerWithoutRounding(arguments.argument(1))

        // 4. Let w be ? ToIntegerWithoutRounding(weeks).
        val w = TemporalAOs.toIntegerWithoutRounding(arguments.argument(2))

        // 5. Let d be ? ToIntegerWithoutRounding(days).
        val d = TemporalAOs.toIntegerWithoutRounding(arguments.argument(3))

        // 6. Let h be ? ToIntegerWithoutRounding(hours).
        val h = TemporalAOs.toIntegerWithoutRounding(arguments.argument(4))

        // 7. Let m be ? ToIntegerWithoutRounding(minutes).
        val m = TemporalAOs.toIntegerWithoutRounding(arguments.argument(5))

        // 8. Let s be ? ToIntegerWithoutRounding(seconds).
        val s = TemporalAOs.toIntegerWithoutRounding(arguments.argument(6))

        // 9. Let ms be ? ToIntegerWithoutRounding(milliseconds).
        val ms = TemporalAOs.toIntegerWithoutRounding(arguments.argument(7))

        // 10. Let mis be ? ToIntegerWithoutRounding(microseconds).
        val mis = TemporalAOs.toIntegerWithoutRounding(arguments.argument(8))

        // 11. Let ns be ? ToIntegerWithoutRounding(nanoseconds).
        val ns = TemporalAOs.toIntegerWithoutRounding(arguments.argument(9))

        // 12. Return ? CreateTemporalDuration(y, mo, w, d, h, m, s, ms, mis, ns, NewTarget).
        return TemporalAOs.createTemporalDuration(
            TemporalAOs.DurationRecord(y, mo, w, d, h, m, s, ms, mis, ns.toBigInteger()), 
            arguments.newTarget as JSObject,
        )
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSDurationCtor(realm).initialize()

        @JvmStatic
        @ECMAImpl("7.2.2")
        fun from(arguments: JSArguments): JSValue {
            val item = arguments.argument(0)

            // 1. If Type(item) is Object and item has an [[InitializedTemporalDuration]] internal slot, then
            if (item is JSObject && Slot.InitializedTemporalDuration in item) {
                // a. Return ! CreateTemporalDuration(item.[[Years]], item.[[Months]], item.[[Weeks]], item.[[Days]], item.[[Hours]], item.[[Minutes]], item.[[Seconds]], item.[[Milliseconds]], item.[[Microseconds]], item.[[Nanoseconds]]).
                return TemporalAOs.createTemporalDuration(TemporalAOs.DurationRecord(
                    item[Slot.Years],
                    item[Slot.Months],
                    item[Slot.Weeks],
                    item[Slot.Days],
                    item[Slot.Hours],
                    item[Slot.Minutes],
                    item[Slot.Seconds],
                    item[Slot.Milliseconds],
                    item[Slot.Microseconds],
                    item[Slot.Nanoseconds],
                ))
            }

            // 2. Return ? ToTemporalDuration(item).
            return TemporalAOs.toTemporalDuration(item)
        }

        @JvmStatic
        @ECMAImpl("7.2.3")
        fun compare(arguments: JSArguments): JSValue {
            // 1. Set one to ? ToTemporalDuration(one).
            val one = TemporalAOs.toTemporalDuration(arguments.argument(0))
            
            // 2. Set two to ? ToTemporalDuration(two).
            val two = TemporalAOs.toTemporalDuration(arguments.argument(1))

            // 3. Set options to ? GetOptionsObject(options).
            val options = TemporalAOs.getOptionsObject(arguments.argument(2))

            // 4. Let relativeTo be ? ToRelativeTemporalObject(options).
            val relativeTo = TemporalAOs.toRelativeTemporalObject(options)

            // 5. Let shift1 be ? CalculateOffsetShift(relativeTo, one.[[Years]], one.[[Months]], one.[[Weeks]], one.[[Days]]).
            val shift1 = TemporalAOs.calculateOffsetShift(relativeTo, one[Slot.Years], one[Slot.Months], one[Slot.Weeks], one[Slot.Days])
            
            // 6. Let shift2 be ? CalculateOffsetShift(relativeTo, two.[[Years]], two.[[Months]], two.[[Weeks]], two.[[Days]]).
            val shift2 = TemporalAOs.calculateOffsetShift(relativeTo, two[Slot.Years], two[Slot.Months], two[Slot.Weeks], two[Slot.Days])

            var days1: Int
            var days2: Int

            // 7. If any of one.[[Years]], two.[[Years]], one.[[Months]], two.[[Months]], one.[[Weeks]], or two.[[Weeks]] are not 0, then
            if (listOf(one[Slot.Years], one[Slot.Months], one[Slot.Weeks], two[Slot.Years], two[Slot.Months], two[Slot.Weeks]).any { it != 0 }) {
                // a. Let unbalanceResult1 be ? UnbalanceDurationRelative(one.[[Years]], one.[[Months]], one.[[Weeks]], one.[[Days]], "day", relativeTo).
                val unbalanceResult1 = TemporalAOs.unbalanceDurationRelative(one[Slot.Years], one[Slot.Months], one[Slot.Weeks], one[Slot.Days], "day", relativeTo)
                
                // b. Let unbalanceResult2 be ? UnbalanceDurationRelative(two.[[Years]], two.[[Months]], two.[[Weeks]], two.[[Days]], "day", relativeTo).
                val unbalanceResult2 = TemporalAOs.unbalanceDurationRelative(two[Slot.Years], two[Slot.Months], two[Slot.Weeks], two[Slot.Days], "day", relativeTo)

                // c. Let days1 be unbalanceResult1.[[Days]].
                days1 = unbalanceResult1.days

                // d. Let days2 be unbalanceResult2.[[Days]].
                days2 = unbalanceResult2.days
            }
            // 8. Else,
            else {
                // a. Let days1 be one.[[Days]].
                days1 = one[Slot.Days]

                // b. Let days2 be two.[[Days]].
                days2 = two[Slot.Days]
            }

            // 9. Let ns1 be ! TotalDurationNanoseconds(days1, one.[[Hours]], one.[[Minutes]], one.[[Seconds]], one.[[Milliseconds]], one.[[Microseconds]], one.[[Nanoseconds]], shift1).
            val ns1 = TemporalAOs.totalDurationNanoseconds(days1, one[Slot.Hours], one[Slot.Minutes], one[Slot.Seconds], one[Slot.Milliseconds], one[Slot.Microseconds], one[Slot.Nanoseconds], shift1)
            
            // 10. Let ns2 be ! TotalDurationNanoseconds(days2, two.[[Hours]], two.[[Minutes]], two.[[Seconds]], two.[[Milliseconds]], two.[[Microseconds]], two.[[Nanoseconds]], shift2).
            val ns2 = TemporalAOs.totalDurationNanoseconds(days2, two[Slot.Hours], two[Slot.Minutes], two[Slot.Seconds], two[Slot.Milliseconds], two[Slot.Microseconds], two[Slot.Nanoseconds], shift2)

            // 11. If ns1 > ns2, return 1𝔽.
            if (ns1 > ns2) 
                return 1.toValue()

            // 12. If ns1 < ns2, return -1𝔽.
            if (ns1 < ns2)
                return (-1).toValue()

            // 13. Return +0𝔽.
            return 0.toValue()
        }
    }
}
