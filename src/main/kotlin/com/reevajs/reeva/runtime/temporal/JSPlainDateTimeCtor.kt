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
import com.reevajs.reeva.utils.*

class JSPlainDateTimeCtor private constructor(realm: Realm) : JSNativeFunction(realm, "PlainDateTime", 3) {
    override fun init() {
        super.init()

        defineBuiltin("from", 1, ::from)
        defineBuiltin("compare", 2, ::compare)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        // 1. If NewTarget is undefined, then
        if (arguments.newTarget == JSUndefined) {
            // a. Throw a TypeError exception.
            Errors.CtorCallWithoutNew("PlainDate").throwTypeError(realm)
        }

        // 2. Let isoYear be ? ToIntegerThrowOnInfinity(isoYear).
        val isoYear = TemporalAOs.toIntegerThrowOnInfinity(arguments.argument(0))

        // 3. Let isoMonth be ? ToIntegerThrowOnInfinity(isoMonth).
        val isoMonth = TemporalAOs.toIntegerThrowOnInfinity(arguments.argument(1))

        // 4. Let isoDay be ? ToIntegerThrowOnInfinity(isoDay).
        val isoDay = TemporalAOs.toIntegerThrowOnInfinity(arguments.argument(2))

        // 5. Let hour be ? ToIntegerThrowOnInfinity(hour).
        val hour = TemporalAOs.toIntegerThrowOnInfinity(arguments.argument(3))

        // 6. Let minute be ? ToIntegerThrowOnInfinity(minute).
        val minute = TemporalAOs.toIntegerThrowOnInfinity(arguments.argument(4))

        // 7. Let second be ? ToIntegerThrowOnInfinity(second).
        val second = TemporalAOs.toIntegerThrowOnInfinity(arguments.argument(5))

        // 8. Let millisecond be ? ToIntegerThrowOnInfinity(millisecond).
        val millisecond = TemporalAOs.toIntegerThrowOnInfinity(arguments.argument(6))

        // 9. Let microsecond be ? ToIntegerThrowOnInfinity(microsecond).
        val microsecond = TemporalAOs.toIntegerThrowOnInfinity(arguments.argument(7))

        // 10. Let nanosecond be ? ToIntegerThrowOnInfinity(nanosecond).
        val nanosecond = TemporalAOs.toIntegerThrowOnInfinity(arguments.argument(8)).toBigInteger()

        // 11. Let calendar be ? ToTemporalCalendarWithISODefault(calendarLike).
        val calendar = TemporalAOs.toTemporalCalendarWithISODefault(arguments.argument(9))

        // 12. Return ? CreateTemporalDateTime(isoYear, isoMonth, isoDay, hour, minute, second, millisecond, microsecond, nanosecond, calendar, NewTarget).
        return TemporalAOs.createTemporalDateTime(isoYear, isoMonth, isoDay, hour, minute, second, millisecond, microsecond, nanosecond, calendar, arguments.newTarget)
    }

    companion object {
        @JvmStatic
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSPlainDateTimeCtor(realm).initialize()

        @JvmStatic
        @ECMAImpl("5.2.2")
        fun from(arguments: JSArguments): JSValue {
            val item = arguments.argument(0)

            // 1. Set options to ? GetOptionsObject(options).
            val options = TemporalAOs.getOptionsObject(arguments.argument(1))

            // 2. If Type(item) is Object and item has an [[InitializedTemporalDateTime]] internal slot, then
            if (item is JSObject && Slot.InitializedTemporalDateTime in item) {
                // a. Perform ? ToTemporalOverflow(options).
                TemporalAOs.toTemporalOverflow(options)

                // b. Return ! CreateTemporalDateTime(item.[[ISOYear]], item.[[ISOMonth]], item.[[ISODay]], item.[[ISOHour]], item.[[ISOMinute]], item.[[ISOSecond]], item.[[ISOMillisecond]], item.[[ISOMicrosecond]], item.[[ISONanosecond]], item.[[Calendar]]).
                return TemporalAOs.createTemporalDateTime(item[Slot.ISOYear], item[Slot.ISOMonth], item[Slot.ISODay], item[Slot.ISOHour], item[Slot.ISOMinute], item[Slot.ISOSecond], item[Slot.ISOMillisecond], item[Slot.ISOMicrosecond], item[Slot.ISONanosecond], item[Slot.Calendar])
            }

            // 3. Return ? ToTemporalDateTime(item, options).
            return TemporalAOs.toTemporalDateTime(item, options)
        }

        @JvmStatic
        @ECMAImpl("5.2.3")
        fun compare(arguments: JSArguments): JSValue {
            // 1. Set one to ? ToTemporalDateTime(one).
            val one = TemporalAOs.toTemporalDateTime(arguments.argument(0))

            // 2. Set two to ? ToTemporalDateTime(two).
            val two = TemporalAOs.toTemporalDateTime(arguments.argument(1))

            // 3. Return 𝔽(! CompareISODateTime(one.[[ISOYear]], one.[[ISOMonth]], one.[[ISODay]], one.[[ISOHour]], one.[[ISOMinute]], one.[[ISOSecond]], one.[[ISOMillisecond]], one.[[ISOMicrosecond]], one.[[ISONanosecond]], two.[[ISOYear]], two.[[ISOMonth]], two.[[ISODay]], two.[[ISOHour]], two.[[ISOMinute]], two.[[ISOSecond]], two.[[ISOMillisecond]], two.[[ISOMicrosecond]], two.[[ISONanosecond]])).
            return TemporalAOs.compareISODateTime(
                one[Slot.ISOYear], one[Slot.ISOMonth], one[Slot.ISODay], one[Slot.ISOHour], one[Slot.ISOMinute], one[Slot.ISOSecond], one[Slot.ISOMillisecond], one[Slot.ISOMicrosecond], one[Slot.ISONanosecond],
                two[Slot.ISOYear], two[Slot.ISOMonth], two[Slot.ISODay], two[Slot.ISOHour], two[Slot.ISOMinute], two[Slot.ISOSecond], two[Slot.ISOMillisecond], two[Slot.ISOMicrosecond], two[Slot.ISONanosecond],
            ).toValue()
        }
    }
}