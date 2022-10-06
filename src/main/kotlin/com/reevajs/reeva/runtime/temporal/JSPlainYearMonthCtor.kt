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

class JSPlainYearMonthCtor private constructor(realm: Realm) : JSNativeFunction(realm, "PlainYearMonth", 2) {
    override fun init() {
        super.init() 

        defineBuiltin("from", 1, ::from)
        defineBuiltin("compare", 2, ::compare)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        // 1. If NewTarget is undefined, then
        if (arguments.newTarget == JSUndefined) {
            // a. Throw a TypeError exception.
            Errors.CtorCallWithoutNew("PlainYearMonth").throwTypeError(realm)
        }

        // 2. If referenceISODay is undefined, then
        // a. Set referenceISODay to 1𝔽.
        val referenceISODay = arguments.argument(3).let {
            if (it == JSUndefined) 1.toValue() else it
        }

        // 3. Let y be ? ToIntegerThrowOnInfinity(isoYear).
        val y = TemporalAOs.toIntegerThrowOnInfinity(arguments.argument(0))
        
        // 4. Let m be ? ToIntegerThrowOnInfinity(isoMonth).
        val m = TemporalAOs.toIntegerThrowOnInfinity(arguments.argument(1))

        // 5. Let calendar be ? ToTemporalCalendarWithISODefault(calendarLike).
        val calendar = TemporalAOs.toTemporalCalendarWithISODefault(arguments.argument(2))

        // 6. Let ref be ? ToIntegerThrowOnInfinity(referenceISODay).
        val ref = TemporalAOs.toIntegerThrowOnInfinity(referenceISODay)

        // 7. Return ? CreateTemporalYearMonth(y, m, calendar, ref, NewTarget).
        return TemporalAOs.createTemporalYearMonth(y, m, calendar, ref, arguments.newTarget as JSObject)
    }

    companion object {
        @JvmStatic
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSPlainYearMonthCtor(realm).initialize()

        @JvmStatic
        @ECMAImpl("9.2.2")
        fun from(arguments: JSArguments): JSValue {
            val item = arguments.argument(0)

            // 1. Set options to ? GetOptionsObject(options).
            val options = TemporalAOs.getOptionsObject(arguments.argument(1))

            // 2. If Type(item) is Object and item has an [[InitializedTemporalYearMonth]] internal slot, then
            if (item is JSObject && Slot.InitializedTemporalYearMonth in item) {
                // a. Perform ? ToTemporalOverflow(options).
                TemporalAOs.toTemporalOverflow(options)

                // b. Return ! CreateTemporalYearMonth(item.[[ISOYear]], item.[[ISOMonth]], item.[[Calendar]], item.[[ISODay]]).
                TemporalAOs.createTemporalYearMonth(item[Slot.ISOYear], item[Slot.ISOMonth], item[Slot.Calendar], item[Slot.ISODay])
            }

            // 3. Return ? ToTemporalYearMonth(item, options).
            return TemporalAOs.toTemporalYearMonth(item, options)
        }

        @JvmStatic
        @ECMAImpl("9.2.3")
        fun compare(arguments: JSArguments): JSValue {
            // 1. Set one to ? ToTemporalYearMonth(one).
            val one = TemporalAOs.toTemporalYearMonth(arguments.argument(0))

            // 2. Set two to ? ToTemporalYearMonth(two).
            val two = TemporalAOs.toTemporalYearMonth(arguments.argument(1))

            // 3. Return 𝔽(! CompareISODate(one.[[ISOYear]], one.[[ISOMonth]], one.[[ISODay]], two.[[ISOYear]], two.[[ISOMonth]], two.[[ISODay]])).
            return TemporalAOs.compareISODate(
                one[Slot.ISOYear], one[Slot.ISOMonth], one[Slot.ISODay],
                two[Slot.ISOYear], two[Slot.ISOMonth], two[Slot.ISODay],
            ).toValue()
        }
    }
}
