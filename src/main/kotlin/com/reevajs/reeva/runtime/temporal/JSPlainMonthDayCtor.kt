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

class JSPlainMonthDayCtor private constructor(realm: Realm) : JSNativeFunction(realm, "PlainMonthDay", 2) {
    override fun init() {
        super.init() 

        defineBuiltin("from", 1, ::from)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        var referenceISOYear = arguments.argument(3)

        // 1. If NewTarget is undefined, then
        if (arguments.newTarget == JSUndefined) {
            // a. Throw a TypeError exception.
            Errors.CtorCallWithoutNew("PlainMonthDay").throwTypeError(realm)
        }

        // 2. If referenceISOYear is undefined, then
        if (referenceISOYear == JSUndefined) {
            // a. Set referenceISOYear to 1972𝔽.
            referenceISOYear = 1972.toValue()
        }

        // 3. Let m be ? ToIntegerThrowOnInfinity(isoMonth).
        val m = TemporalAOs.toIntegerThrowOnInfinity(arguments.argument(0))

        // 4. Let d be ? ToIntegerThrowOnInfinity(isoDay).
        val d = TemporalAOs.toIntegerThrowOnInfinity(arguments.argument(1))

        // 5. Let calendar be ? ToTemporalCalendarWithISODefault(calendarLike).
        val calendar = TemporalAOs.toTemporalCalendarWithISODefault(arguments.argument(2))

        // 6. Let ref be ? ToIntegerThrowOnInfinity(referenceISOYear).
        val ref = TemporalAOs.toIntegerThrowOnInfinity(referenceISOYear)

        // 7. Return ? CreateTemporalMonthDay(m, d, calendar, ref, NewTarget).
        return TemporalAOs.createTemporalMonthDay(m, d, calendar, ref, arguments.newTarget as JSObject)
    }

    companion object {
        @JvmStatic
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSPlainMonthDayCtor(realm).initialize()

        @JvmStatic
        @ECMAImpl("9.2.2")
        fun from(arguments: JSArguments): JSValue {
            val item = arguments.argument(0)

            // 1. Set options to ? GetOptionsObject(options).
            val options = TemporalAOs.getOptionsObject(arguments.argument(1))

            // 2. If Type(item) is Object and item has an [[InitializedTemporalMonthDay]] internal slot, then
            if (item is JSObject && Slot.InitializedTemporalMonthDay in item) {
                // a. Perform ? ToTemporalOverflow(options).
                TemporalAOs.toTemporalOverflow(options)

                // b. Return ! CreateTemporalMonthDay(item.[[ISOMonth]], item.[[ISODay]], item.[[Calendar]], item.[[ISOYear]]).
                TemporalAOs.createTemporalMonthDay(item[Slot.ISOMonth], item[Slot.ISODay], item[Slot.Calendar], item[Slot.ISOYear])
            }

            // 3. Return ? ToTemporalMonthDay(item, options).
            return TemporalAOs.toTemporalMonthDay(item, options)
        }
    }
}
