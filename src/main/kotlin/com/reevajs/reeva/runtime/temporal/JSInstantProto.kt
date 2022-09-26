package com.reevajs.reeva.runtime.temporal

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.attrs
import com.reevajs.reeva.utils.toValue
import java.math.BigDecimal
import java.math.BigInteger

class JSInstantProto(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("constructor", realm.instantCtor)
        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "Temporal.Instant".toValue(), attrs { +conf })

        defineBuiltinGetter("epochSeconds", ::getEpochSeconds)
        defineBuiltinGetter("epochMilliseconds", ::getEpochMilliseconds)
        defineBuiltinGetter("epochMicroseconds", ::getEpochMicroseconds)
        defineBuiltinGetter("epochNanoseconds", ::getEpochNanoseconds)

        defineBuiltin("add", 1, ::add)
        defineBuiltin("subtract", 1, ::subtract)
    }

    companion object {
        fun create(realm: Realm) = JSInstantProto(realm).initialize()

        private fun thisInstant(thisValue: JSValue, method: String): JSObject {
            if (!AOs.requireInternalSlot(thisValue, Slot.InitializedTemporalInstant))
                Errors.IncompatibleMethodCall("Instant.prototype.$method").throwTypeError()
            return thisValue
        }

        private fun thisNanoseconds(thisValue: JSValue, method: String): BigInteger {
            return thisInstant(thisValue, method)[Slot.Nanoseconds]
        }

        @JvmStatic
        @ECMAImpl("8.3.3")
        fun getEpochSeconds(arguments: JSArguments): JSValue {
            // 1. Let instant be the this value.
            // 2. Perform ? RequireInternalSlot(instant, [[InitializedTemporalInstant]]).
            // 3. Let ns be instant.[[Nanoseconds]].
            val ns = thisNanoseconds(arguments.thisValue, "get epochSeconds")

            // 4. Let s be RoundTowardsZero(ℝ(ns) / 10^9).
            // 5. Return 𝔽(s).
            val seconds = ns.toBigDecimal().divide(BigDecimal.valueOf(1_000_000_000L))
            return TemporalAOs.roundTowardsZero(seconds).toValue()
        }

        @JvmStatic
        @ECMAImpl("8.3.4")
        fun getEpochMilliseconds(arguments: JSArguments): JSValue {
            // 1. Let instant be the this value.
            // 2. Perform ? RequireInternalSlot(instant, [[InitializedTemporalInstant]]).
            // 3. Let ns be instant.[[Nanoseconds]].
            val ns = thisNanoseconds(arguments.thisValue, "get epochSeconds")

            // 4. Let ms be RoundTowardsZero(ℝ(ns) / 10^6).
            // 5. Return 𝔽(ms).
            val millis = ns.toBigDecimal().divide(BigDecimal.valueOf(1_000_000L))
            return TemporalAOs.roundTowardsZero(millis).toValue()
        }

        @JvmStatic
        @ECMAImpl("8.3.5")
        fun getEpochMicroseconds(arguments: JSArguments): JSValue {
            // 1. Let instant be the this value.
            // 2. Perform ? RequireInternalSlot(instant, [[InitializedTemporalInstant]]).
            // 3. Let ns be instant.[[Nanoseconds]].
            val ns = thisNanoseconds(arguments.thisValue, "get epochSeconds")

            // 4. Let µs be RoundTowardsZero(ℝ(ns) / 103^).
            // 5. Return ℤ(µs).
            val micros = ns.toBigDecimal().divide(BigDecimal.valueOf(1000L))
            return TemporalAOs.roundTowardsZero(micros).toValue()
        }

        @JvmStatic
        @ECMAImpl("8.3.6")
        fun getEpochNanoseconds(arguments: JSArguments): JSValue {
            // 1. Let instant be the this value.
            // 2. Perform ? RequireInternalSlot(instant, [[InitializedTemporalInstant]]).
            // 3. Let ns be instant.[[Nanoseconds]].
            // 4. Return ns.
            return thisNanoseconds(arguments.thisValue, "get epochSeconds").toValue()
        }

        @JvmStatic
        @ECMAImpl("8.3.7")
        fun add(arguments: JSArguments): JSValue {
            // 1. Let instant be the this value.
            // 2. Perform ? RequireInternalSlot(instant, [[InitializedTemporalInstant]]).
            val instant = thisInstant(arguments.thisValue, "add")

            // 3. Return ? AddDurationToOrSubtractDurationFromInstant(add, instant, temporalDurationLike).
            return TemporalAOs.addDurationToOrSubtractDurationFromInstant(true, instant, arguments.argument(0))
        }

        @JvmStatic
        @ECMAImpl("")
        fun subtract(arguments: JSArguments): JSValue {
            // 1. Let instant be the this value.
            // 2. Perform ? RequireInternalSlot(instant, [[InitializedTemporalInstant]]).
            val instant = thisInstant(arguments.thisValue, "subtract")

            // 3. Return ? AddDurationToOrSubtractDurationFromInstant(subtract, instant, temporalDurationLike).
            return TemporalAOs.addDurationToOrSubtractDurationFromInstant(false, instant, arguments.argument(0))
        }

        @JvmStatic
        @ECMAImpl("")
        fun until(arguments: JSArguments): JSValue {
            // 1. Let instant be the this value.
            // 2. Perform ? RequireInternalSlot(instant, [[InitializedTemporalInstant]]).
            val instant = thisInstant(arguments.thisValue, "until")

            // 3. Return ? DifferenceTemporalInstant(until, instant, other, options).
            return TemporalAOs.differenceTemporalInstant(true, instant, arguments.argument(0), arguments.argument(1))
        }
    }
}
