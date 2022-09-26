package com.reevajs.reeva.runtime.temporal

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.toBigInt
import com.reevajs.reeva.runtime.toNumber
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue
import java.math.BigInteger

class JSInstantCtor(realm: Realm) : JSNativeFunction(realm, "Instant", 1) {
    override fun init() {
        super.init()

        defineBuiltin("from", 1, ::from)
        defineBuiltin("fromEpochSeconds", 1, ::fromEpochSeconds)
        defineBuiltin("fromEpochMilliseconds", 1, ::fromEpochMilliseconds)
        defineBuiltin("fromEpochMicroseconds", 1, ::fromEpochMicroseconds)
        defineBuiltin("fromEpochNanoseconds", 1, ::fromEpochNanoseconds)
        defineBuiltin("compare", 1, ::compare)
    }

    @ECMAImpl("8.1.1")
    override fun evaluate(arguments: JSArguments): JSValue {
        // 1. If NewTarget is undefined, then
        if (arguments.newTarget == JSUndefined) {
            // a. Throw a TypeError exception.
            Errors.CtorCallWithoutNew("Instant").throwTypeError(realm)
        }

        // 2. Let epochNanoseconds be ? ToBigInt(epochNanoseconds).
        val epochNanoseconds = arguments.argument(0).toBigInt()

        // 3. If ! IsValidEpochNanoseconds(epochNanoseconds) is false, throw a RangeError exception.
        if (!TemporalAOs.isValidEpochNanoseconds(epochNanoseconds.number))
            Errors.TODO("InstantCtor").throwRangeError(realm)

        // 4. Return ? CreateTemporalInstant(epochNanoseconds, NewTarget).
        return TemporalAOs.createTemporalInstant(epochNanoseconds.number, arguments.newTarget)
    }

    companion object {
        fun create(realm: Realm) = JSInstantCtor(realm).initialize()

        @JvmStatic
        @ECMAImpl("8.2.2")
        fun from(arguments: JSArguments): JSValue {
            val item = arguments.argument(0)

            // 1. If Type(item) is Object and item has an [[InitializedTemporalInstant]] internal slot, then
            if (item is JSObject && Slot.InitializedTemporalInstant in item) {
                // a. Return ! CreateTemporalInstant(item.[[Nanoseconds]]).
                return TemporalAOs.createTemporalInstant(item[Slot.Nanoseconds])
            }

            // 2. Return ? ToTemporalInstant(item).
            return TemporalAOs.toTemporalInstant(item)
        }

        @JvmStatic
        @ECMAImpl("8.2.3")
        fun fromEpochSeconds(arguments: JSArguments): JSValue {
            // 1. Set epochSeconds to ? ToNumber(epochSeconds).
            // 2. Set epochSeconds to ? NumberToBigInt(epochSeconds).
            val epochSeconds = AOs.numberToBigInt(arguments.argument(0).toNumber()).number

            // 3. Let epochNanoseconds be epochSeconds × 10^9ℤ.
            val epochNanoseconds = epochSeconds * BigInteger.TEN.pow(9)

            // 4. If ! IsValidEpochNanoseconds(epochNanoseconds) is false, throw a RangeError exception.
            if (!TemporalAOs.isValidEpochNanoseconds(epochNanoseconds)) {
                Errors.Temporal.InvalidEpochNanoseconds(epochNanoseconds.toString(10))
                    .throwRangeError(Agent.activeAgent.getActiveRealm())
            }

            // 5. Return ! CreateTemporalInstant(epochNanoseconds).
            return TemporalAOs.createTemporalInstant(epochNanoseconds)
        }

        @JvmStatic
        @ECMAImpl("8.2.4")
        fun fromEpochMilliseconds(arguments: JSArguments): JSValue {
            // 1. Set epochMilliseconds to ? ToNumber(epochMilliseconds).
            // 2. Set epochMilliseconds to ? NumberToBigInt(epochMilliseconds).
            val epochMilliseconds = AOs.numberToBigInt(arguments.argument(0).toNumber()).number

            // 3. Let epochNanoseconds be epochMilliseconds × 10^6ℤ.
            val epochNanoseconds = epochMilliseconds * BigInteger.TEN.pow(6)

            // 4. If ! IsValidEpochNanoseconds(epochNanoseconds) is false, throw a RangeError exception.
            if (!TemporalAOs.isValidEpochNanoseconds(epochNanoseconds)) {
                Errors.Temporal.InvalidEpochNanoseconds(epochNanoseconds.toString(10))
                    .throwRangeError(Agent.activeAgent.getActiveRealm())
            }

            // 5. Return ! CreateTemporalInstant(epochNanoseconds).
            return TemporalAOs.createTemporalInstant(epochNanoseconds)
        }

        @JvmStatic
        @ECMAImpl("8.2.5")
        fun fromEpochMicroseconds(arguments: JSArguments): JSValue {
            // 1. Set epochMicroseconds to ? ToBigInt(epochMicroseconds).
            val epochMicroseconds = AOs.numberToBigInt(arguments.argument(0).toNumber()).number

            // 2. Let epochNanoseconds be epochMicroseconds × 1000ℤ.
            val epochNanoseconds = epochMicroseconds * BigInteger.valueOf(1000L)

            // 3. If ! IsValidEpochNanoseconds(epochNanoseconds) is false, throw a RangeError exception.
            if (!TemporalAOs.isValidEpochNanoseconds(epochNanoseconds)) {
                Errors.Temporal.InvalidEpochNanoseconds(epochNanoseconds.toString(10))
                    .throwRangeError(Agent.activeAgent.getActiveRealm())
            }

            // 4. Return ! CreateTemporalInstant(epochNanoseconds).
            return TemporalAOs.createTemporalInstant(epochNanoseconds)
        }

        @JvmStatic
        @ECMAImpl("8.2.6")
        fun fromEpochNanoseconds(arguments: JSArguments): JSValue {
            // 1. Set epochNanoseconds to ? ToBigInt(epochNanoseconds).
            val epochNanoseconds = AOs.numberToBigInt(arguments.argument(0).toNumber()).number

            // 2. If ! IsValidEpochNanoseconds(epochNanoseconds) is false, throw a RangeError exception.
            if (!TemporalAOs.isValidEpochNanoseconds(epochNanoseconds)) {
                Errors.Temporal.InvalidEpochNanoseconds(epochNanoseconds.toString(10))
                    .throwRangeError(Agent.activeAgent.getActiveRealm())
            }

            // 3. Return ! CreateTemporalInstant(epochNanoseconds).
            return TemporalAOs.createTemporalInstant(epochNanoseconds)
        }

        @JvmStatic
        @ECMAImpl("8.2.7")
        fun compare(arguments: JSArguments): JSValue {
            // 1. Set one to ? ToTemporalInstant(one).
            val one = TemporalAOs.toTemporalInstant(arguments.argument(0))

            // 2. Set two to ? ToTemporalInstant(two).
            val two = TemporalAOs.toTemporalInstant(arguments.argument(1))

            // 3. Return 𝔽(! CompareEpochNanoseconds(one.[[Nanoseconds]], two.[[Nanoseconds]])).
            return TemporalAOs.compareEpochNanoseconds(
                one[Slot.Nanoseconds],
                two[Slot.Nanoseconds],
            ).toValue()
        }
    }
}
