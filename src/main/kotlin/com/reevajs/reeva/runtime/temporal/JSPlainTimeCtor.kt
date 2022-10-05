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

class JSPlainTimeCtor private constructor(realm: Realm) : JSNativeFunction(realm, "PlainTime", 3) {
    override fun init() {
        super.init()

        defineBuiltin("from", 1, ::from)
        defineBuiltin("compare", 2, ::compare)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        // 1. If NewTarget is undefined, then
        if (arguments.newTarget == JSUndefined) {
            // a. Throw a TypeError exception.
            Errors.CtorCallWithoutNew("PlainTime").throwTypeError(realm)
        }

        // 2. Let hour be ? ToIntegerThrowOnInfinity(hour).
        val hour = TemporalAOs.toIntegerThrowOnInfinity(arguments.argument(0))

        // 3. Let minute be ? ToIntegerThrowOnInfinity(minute).
        val minute = TemporalAOs.toIntegerThrowOnInfinity(arguments.argument(1))

        // 4. Let second be ? ToIntegerThrowOnInfinity(second).
        val second = TemporalAOs.toIntegerThrowOnInfinity(arguments.argument(2))

        // 5. Let millisecond be ? ToIntegerThrowOnInfinity(millisecond).
        val millisecond = TemporalAOs.toIntegerThrowOnInfinity(arguments.argument(3))

        // 6. Let microsecond be ? ToIntegerThrowOnInfinity(microsecond).
        val microsecond = TemporalAOs.toIntegerThrowOnInfinity(arguments.argument(4))

        // 7. Let nanosecond be ? ToIntegerThrowOnInfinity(nanosecond).
        val nanosecond = TemporalAOs.toIntegerThrowOnInfinity(arguments.argument(5)).toBigInteger()

        // 8. Return ? CreateTemporalTime(hour, minute, second, millisecond, microsecond, nanosecond, NewTarget).
        return TemporalAOs.createTemporalTime(hour, minute, second, millisecond, microsecond, nanosecond, arguments.newTarget as JSObject)
    }

    companion object {
        @JvmStatic
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSPlainTimeCtor(realm).initialize()

        @JvmStatic
        @ECMAImpl("4.2.2")
        fun from(arguments: JSArguments): JSValue {
            val item = arguments.argument(0)
            var options = arguments.argument(1)

            // 1. Set options to ? GetOptionsObject(options).
            options = TemporalAOs.getOptionsObject(options)

            // 2. Let overflow be ? ToTemporalOverflow(options).
            val overflow = TemporalAOs.toTemporalOverflow(options)

            // 3. If Type(item) is Object and item has an [[InitializedTemporalTime]] internal slot, then
            if (item is JSObject && Slot.InitializedTemporalDate in item) {
                // a. Return ! CreateTemporalTime(item.[[ISOHour]], item.[[ISOMinute]], item.[[ISOSecond]], item.[[ISOMillisecond]], item.[[ISOMicrosecond]], item.[[ISONanosecond]]).
                return TemporalAOs.createTemporalTime(item[Slot.ISOHour], item[Slot.ISOMinute], item[Slot.ISOSecond], item[Slot.ISOMillisecond], item[Slot.ISOMicrosecond], item[Slot.ISONanosecond])
            }

            // 4. Return ? ToTemporalTime(item, overflow).
            return TemporalAOs.toTemporalDate(item, options)
        }

        @JvmStatic
        @ECMAImpl("4.2.3")
        fun compare(arguments: JSArguments): JSValue {
            // 1. Set one to ? ToTemporalTime(one).
            val one = TemporalAOs.toTemporalTime(arguments.argument(0))

            // 2. Set two to ? ToTemporalTime(two).
            val two = TemporalAOs.toTemporalTime(arguments.argument(1))

            // 3. Return 𝔽(! CompareTemporalTime(one.[[ISOHour]], one.[[ISOMinute]], one.[[ISOSecond]], one.[[ISOMillisecond]], one.[[ISOMicrosecond]], one.[[ISONanosecond]], two.[[ISOHour]], two.[[ISOMinute]], two.[[ISOSecond]], two.[[ISOMillisecond]], two.[[ISOMicrosecond]], two.[[ISONanosecond]])).
            return TemporalAOs.compareTemporalTime(
                one[Slot.ISOHour], one[Slot.ISOMinute], one[Slot.ISOSecond], one[Slot.ISOMillisecond], one[Slot.ISOMicrosecond], one[Slot.ISONanosecond],
                two[Slot.ISOHour], two[Slot.ISOMinute], two[Slot.ISOSecond], two[Slot.ISOMillisecond], two[Slot.ISOMicrosecond], two[Slot.ISONanosecond],
            ).toValue()
        }
    }
}
