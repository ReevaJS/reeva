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

class JSPlainDateCtor private constructor(realm: Realm) : JSNativeFunction(realm, "PlainDate", 3) {
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

        // 2. Let y be ? ToIntegerThrowOnInfinity(isoYear).
        val y = TemporalAOs.toIntegerThrowOnInfinity(arguments.argument(0))
        
        // 3. Let m be ? ToIntegerThrowOnInfinity(isoMonth).
        val m = TemporalAOs.toIntegerThrowOnInfinity(arguments.argument(1))
        
        // 4. Let d be ? ToIntegerThrowOnInfinity(isoDay).
        val d = TemporalAOs.toIntegerThrowOnInfinity(arguments.argument(2))

        // 5. Let calendar be ? ToTemporalCalendarWithISODefault(calendarLike).
        val calendar = TemporalAOs.toTemporalCalendarWithISODefault(arguments.argument(3))

        // 6. Return ? CreateTemporalDate(y, m, d, calendar, NewTarget).
        return TemporalAOs.createTemporalDate(y, m, d, calendar, arguments.newTarget as JSObject)
    }

    companion object {
        @JvmStatic
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSPlainDateCtor(realm).initialize()

        @JvmStatic
        @ECMAImpl("3.2.2")
        fun from(arguments: JSArguments): JSValue {
            val item = arguments.argument(0)
            var options = arguments.argument(1)

            // 1. Set options to ? GetOptionsObject(options).
            options = TemporalAOs.getOptionsObject(options)

            // 2. If Type(item) is Object and item has an [[InitializedTemporalDate]] internal slot, then
            if (item is JSObject && Slot.InitializedTemporalDate in item) {
                // a. Perform ? ToTemporalOverflow(options).
                TemporalAOs.toTemporalOverflow(options)

                // b. Return ! CreateTemporalDate(item.[[ISOYear]], item.[[ISOMonth]], item.[[ISODay]], item.[[Calendar]]).
                return TemporalAOs.createTemporalDate(item[Slot.ISOYear], item[Slot.ISOMonth], item[Slot.ISODay], item[Slot.Calendar])
            }

            // 3. Return ? ToTemporalDate(item, options).
            return TemporalAOs.toTemporalDate(item, options)
        }

        @JvmStatic
        @ECMAImpl("3.2.3")
        fun compare(arguments: JSArguments): JSValue {
            // 1. Set one to ? ToTemporalDate(one).
            val one = TemporalAOs.toTemporalDate(arguments.argument(0))

            // 2. Set two to ? ToTemporalDate(two).
            val two = TemporalAOs.toTemporalDate(arguments.argument(1))

            // 3. Return 𝔽(! CompareISODate(one.[[ISOYear]], one.[[ISOMonth]], one.[[ISODay]], two.[[ISOYear]], two.[[ISOMonth]], two.[[ISODay]])).
            return TemporalAOs.compareISODate(one[Slot.ISOYear], one[Slot.ISOMonth], one[Slot.ISODay], two[Slot.ISOYear], two[Slot.ISOMonth], two[Slot.ISODay]).toValue()
        }
    }
}
