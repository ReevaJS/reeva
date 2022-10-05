package com.reevajs.reeva.runtime.other

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.JSNumber
import com.reevajs.reeva.runtime.primitives.JSString
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.toValue
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatterBuilder
import java.time.format.SignStyle
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAccessor
import java.time.temporal.TemporalField

// TODO: Code deduplication
class JSDateCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Date", 7) {
    override fun init() {
        super.init()

        defineBuiltin("now", 0, ::now)
        defineBuiltin("parse", 0, ::parse)
        defineBuiltin("UTC", 0, ::utc)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget == JSUndefined)
            return AOs.toDateString(ZonedDateTime.now()).toValue()

        val zdt = when (arguments.size) {
            0 -> ZonedDateTime.now()
            1 -> {
                val arg = arguments.argument(0)
                if (arg is JSObject && Slot.DateValue in arg) {
                    arg[Slot.DateValue] ?: return JSDateObject.create(null)
                } else {
                    val prim = arg.toPrimitive()
                    if (prim is JSString) {
                        parseHelper(arguments)
                    } else {
                        AOs.timeClip(
                            ZonedDateTime.ofInstant(
                                Instant.ofEpochMilli(prim.toNumber().asLong),
                                AOs.defaultZone
                            )
                        )
                    }
                }
            }
            else -> {
                fun getArg(index: Int): Long? {
                    if (arguments.size <= index)
                        return 0L
                    val value = arguments[index].toNumber()
                    if (!value.isFinite)
                        return null
                    return value.asLong
                }

                val yearArg = arguments.argument(0).toNumber()
                val year = if (yearArg.isNaN) {
                    return JSNumber.NaN
                } else {
                    val yi = yearArg.toIntegerOrInfinity().asLong
                    (if (yi in 0..99) 1900 + yi else yearArg.asLong) - 1970
                }

                val month = getArg(1) ?: return JSNumber.NaN
                val day = (getArg(2) ?: return JSNumber.NaN) - 1L
                val hours = getArg(3) ?: return JSNumber.NaN
                val minutes = getArg(4) ?: return JSNumber.NaN
                val seconds = getArg(5) ?: return JSNumber.NaN
                val millis = getArg(6) ?: return JSNumber.NaN

                val date = LocalDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC).with {
                    it.plus(year, ChronoUnit.YEARS)
                        .plus(month, ChronoUnit.MONTHS)
                        .plus(day, ChronoUnit.DAYS)
                        .plus(hours, ChronoUnit.HOURS)
                        .plus(minutes, ChronoUnit.MINUTES)
                        .plus(seconds, ChronoUnit.SECONDS)
                        .plus(millis, ChronoUnit.MILLIS)
                }

                return JSDateObject.create(AOs.timeClip(ZonedDateTime.of(date, AOs.defaultZone)))
            }
        }

        return AOs.ordinaryCreateFromConstructor(
            arguments.newTarget,
            listOf(Slot.DateValue),
            defaultProto = Realm::dateProto,
        ).also { it[Slot.DateValue] = zdt }
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSDateCtor(realm).initialize()

        @ECMAImpl("21.4.3.1")
        @JvmStatic
        fun now(arguments: JSArguments): JSValue {
            return Instant.now().toEpochMilli().toValue()
        }

        @ECMAImpl("21.4.3.2")
        @JvmStatic
        fun parse(arguments: JSArguments): JSValue {
            return JSDateObject.create(parseHelper(arguments) ?: return JSNumber.NaN)
        }

        private fun parseHelper(arguments: JSArguments): ZonedDateTime? {
            val arg = arguments.argument(0).toJSString().string

            val result = dateTimeStringFormatter.parse(arg)

            val ztd = ZonedDateTime.of(
                result.get(ChronoField.YEAR),
                result.getOrDefault(ChronoField.MONTH_OF_YEAR, 1),
                result.getOrDefault(ChronoField.DAY_OF_MONTH, 1),
                result.getOrDefault(ChronoField.HOUR_OF_DAY),
                result.getOrDefault(ChronoField.MINUTE_OF_HOUR),
                result.getOrDefault(ChronoField.SECOND_OF_MINUTE),
                result.getOrDefault(ChronoField.MILLI_OF_SECOND) * 1_000_000,
                if (result.isSupported(ChronoField.OFFSET_SECONDS)) {
                    ZoneOffset.ofTotalSeconds(result.get(ChronoField.OFFSET_SECONDS))
                } else ZoneOffset.UTC,
            )

            return AOs.timeClip(ztd)
        }

        private fun TemporalAccessor.getOrDefault(field: TemporalField, default: Int = 0): Int {
            return if (isSupported(field)) get(field) else default
        }

        @ECMAImpl("21.4.3.4")
        @JvmStatic
        fun utc(arguments: JSArguments): JSValue {
            fun getArg(index: Int, offset: Int = 0): Long? {
                if (arguments.size <= index)
                    return 0L
                val value = arguments[index].toNumber()
                if (!value.isFinite)
                    return null
                return value.asLong + offset
            }

            val yearArg = arguments.argument(0).toNumber()
            val year = if (!yearArg.isFinite) {
                return JSNumber.NaN
            } else {
                val yi = yearArg.toIntegerOrInfinity().asLong
                (if (yi in 0..99) 1900 + yi else yearArg.asLong) - 1970
            }

            val month = getArg(1) ?: return JSNumber.NaN
            val day = getArg(2, -1) ?: return JSNumber.NaN
            val hours = getArg(3) ?: return JSNumber.NaN
            val minutes = getArg(4) ?: return JSNumber.NaN
            val seconds = getArg(5) ?: return JSNumber.NaN
            val millis = getArg(6) ?: return JSNumber.NaN

            val date = LocalDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC).with {
                it.plus(year, ChronoUnit.YEARS)
                    .plus(month, ChronoUnit.MONTHS)
                    .plus(day, ChronoUnit.DAYS)
                    .plus(hours, ChronoUnit.HOURS)
                    .plus(minutes, ChronoUnit.MINUTES)
                    .plus(seconds, ChronoUnit.SECONDS)
                    .plus(millis, ChronoUnit.MILLIS)
            }

            val zdt = ZonedDateTime.of(date, ZoneOffset.UTC)
            if (AOs.timeClip(zdt) == null)
                return JSNumber.NaN

            return zdt.toInstant().toEpochMilli().toValue()
        }

        internal val dateTimeStringFormatter = DateTimeFormatterBuilder()
            .parseLenient()
            .appendValue(ChronoField.YEAR, 4, 6, SignStyle.NORMAL)
            .parseStrict()
            .optionalStart()
            .appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR, 2)
            .optionalStart()
            .appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH, 2)
            .optionalEnd()
            .optionalEnd()
            .optionalStart()
            .appendLiteral('T')
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .optionalStart()
            .appendLiteral('.')
            .appendValue(ChronoField.MILLI_OF_SECOND, 3)
            .optionalEnd()
            .optionalEnd()
            .optionalEnd()
            .optionalStart()
            .appendZoneId()
            .optionalEnd()
            .toFormatter()
    }
}
