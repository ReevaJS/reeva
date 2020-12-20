package me.mattco.reeva.runtime.builtins

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.primitives.JSNumber
import me.mattco.reeva.runtime.primitives.JSString
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.argument
import me.mattco.reeva.utils.key
import me.mattco.reeva.utils.toValue
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
    init {
        isConstructable = true
    }

    override fun init() {
        super.init()

        defineNativeFunction("now", 0, ::now)
        defineNativeFunction("parse", 0, ::parse)
        defineNativeFunction("UTC", 0, ::utc)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        if (newTarget == JSUndefined)
            return Operations.toDateString(ZonedDateTime.now()).toValue()

        val zdt = when (arguments.size) {
            0 -> ZonedDateTime.now()
            1 -> {
                val arg = arguments.argument(0)
                if (arg is JSDateObject) {
                    arg.dateValue ?: return JSDateObject.create(realm, null)
                } else {
                    val prim = Operations.toPrimitive(arg)
                    if (prim is JSString) {
                        parseHelper(arguments)
                    } else {
                        Operations.timeClip(ZonedDateTime.ofInstant(Instant.ofEpochMilli(Operations.toNumber(prim).asLong), Operations.defaultZone))
                    }
                }
            }
            else -> {
                fun getArg(index: Int): Long? {
                    if (arguments.size <= index)
                        return 0L
                    val value = Operations.toNumber(arguments[index])
                    if (!value.isFinite)
                        return null
                    return value.asLong
                }

                val yearArg = Operations.toNumber(arguments.argument(0))
                val year = if (yearArg.isNaN) {
                    return JSNumber.NaN
                } else {
                    val yi = Operations.toIntegerOrInfinity(yearArg).asLong
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

                return JSDateObject.create(realm, Operations.timeClip(ZonedDateTime.of(date, Operations.defaultZone)))
            }
        }

        return JSDateObject.create(realm, zdt)
    }

    fun now(thisValue: JSValue, arguments: JSArguments): JSValue {
        return Instant.now().toEpochMilli().toValue()
    }

    fun parse(thisValue: JSValue, arguments: JSArguments): JSValue {
        return JSDateObject.create(realm, parseHelper(arguments) ?: return JSNumber.NaN)
    }

    private fun parseHelper(arguments: JSArguments): ZonedDateTime? {
        val arg = Operations.toString(arguments.argument(0)).string

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

        return Operations.timeClip(ztd)
    }

    private fun TemporalAccessor.getOrDefault(field: TemporalField, default: Int = 0): Int {
        return if (isSupported(field)) get(field) else default
    }

    fun utc(thisValue: JSValue, arguments: JSArguments): JSValue {
        fun getArg(index: Int, offset: Int = 0): Long? {
            if (arguments.size <= index)
                return 0L
            val value = Operations.toNumber(arguments[index])
            if (!value.isFinite)
                return null
            return value.asLong + offset
        }

        val yearArg = Operations.toNumber(arguments.argument(0))
        val year = if (!yearArg.isFinite) {
            return JSNumber.NaN
        } else {
            val yi = Operations.toIntegerOrInfinity(yearArg).asLong
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
        if (Operations.timeClip(zdt) == null)
            return JSNumber.NaN


        return zdt.toInstant().toEpochMilli().toValue()
    }

    companion object {
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


        fun create(realm: Realm) = JSDateCtor(realm).initialize()
    }
}
