package me.mattco.reeva.runtime.builtins

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSNull
import me.mattco.reeva.runtime.primitives.JSNumber
import me.mattco.reeva.utils.*
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.TextStyle
import kotlin.math.abs

@Suppress("UNUSED_PARAMETER")
class JSDateProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("constructor", realm.dateCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    @JSMethod("getDate", 0)
    fun getDate(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisTimeValue(thisValue, "getDate")?.dayOfMonth?.toValue() ?: return JSNumber.NaN
    }

    @JSMethod("getDay", 0)
    fun getDay(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisTimeValue(thisValue, "getDay")?.dayOfWeek?.value?.minus(1)?.toValue() ?: return JSNumber.NaN
    }

    @JSMethod("getFullYear", 0)
    fun getFullYear(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisTimeValue(thisValue, "getFullYear")?.year?.toValue() ?: return JSNumber.NaN
    }

    @JSMethod("getHours", 0)
    fun getHours(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisTimeValue(thisValue, "getHours")?.hour?.toValue() ?: return JSNumber.NaN
    }

    @JSMethod("getMilliseconds", 0)
    fun getMilliseconds(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisTimeValue(thisValue, "getMilliseconds")?.nano?.div(1_000_000L)?.toValue() ?: return JSNumber.NaN
    }

    @JSMethod("getMinutes", 0)
    fun getMinutes(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisTimeValue(thisValue, "getMinutes")?.minute?.toValue() ?: return JSNumber.NaN
    }

    @JSMethod("getMonth", 0)
    fun getMonth(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisTimeValue(thisValue, "getMonth")?.monthValue?.toValue() ?: return JSNumber.NaN
    }

    @JSMethod("getSeconds", 0)
    fun getSeconds(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisTimeValue(thisValue, "getSeconds")?.second?.toValue() ?: return JSNumber.NaN
    }

    @JSMethod("getTime", 0)
    fun getTime(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisTimeValue(thisValue, "getTime")?.toInstant()?.toEpochMilli()?.toValue() ?: JSNumber.NaN
    }

    @JSMethod("getTimezoneOffset", 0)
    fun getTimezoneOffset(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisTimeValue(thisValue, "getTimezoneOffset")?.offset?.totalSeconds?.toValue() ?: return JSNumber.NaN
    }

    @JSMethod("getUTCDate", 0)
    fun getUTCDate(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisUTCTimeValue(thisValue, "getUTCDate")?.dayOfMonth?.toValue() ?: return JSNumber.NaN
    }

    @JSMethod("getUTCDay", 0)
    fun getUTCDay(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisUTCTimeValue(thisValue, "getUTCDay")?.dayOfWeek?.value?.minus(1)?.toValue() ?: return JSNumber.NaN
    }

    @JSMethod("getUTCFullYear", 0)
    fun getUTCFullYear(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisUTCTimeValue(thisValue, "getUTCFullYear")?.year?.toValue() ?: return JSNumber.NaN
    }

    @JSMethod("getUTCHours", 0)
    fun getUTCHours(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisUTCTimeValue(thisValue, "getUTCHours")?.hour?.toValue() ?: return JSNumber.NaN
    }

    @JSMethod("getUTCMilliseconds", 0)
    fun getUTCMilliseconds(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisUTCTimeValue(thisValue, "getUTCMilliseconds")?.nano?.div(1_000_000L)?.toValue() ?: return JSNumber.NaN
    }

    @JSMethod("getUTCMinutes", 0)
    fun getUTCMinutes(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisUTCTimeValue(thisValue, "getUTCMinutes")?.minute?.toValue() ?: return JSNumber.NaN
    }

    @JSMethod("getUTCMonth", 0)
    fun getUTCMonth(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisUTCTimeValue(thisValue, "getUTCMonth")?.monthValue?.toValue() ?: return JSNumber.NaN
    }

    @JSMethod("getUTCSeconds", 0)
    fun getUTCSeconds(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisUTCTimeValue(thisValue, "getUTCSeconds")?.second?.toValue() ?: return JSNumber.NaN
    }

    @JSMethod("getUTCTime", 0)
    fun getUTCTime(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisUTCTimeValue(thisValue, "getUTCTime")?.toInstant()?.toEpochMilli()?.toValue() ?: return JSNumber.NaN
    }

    @JSMethod("setDate", 0)
    fun setDate(thisValue: JSValue, arguments: JSArguments): JSValue {
        val zdt = thisTimeValue(thisValue, "setDate") ?: return JSNumber.NaN
        expect(thisValue is JSDateObject)
        val days = Operations.toNumber(arguments.argument(0))
        ifAnyNotFinite(thisValue, days) { return it }
        return dateValueSetHelper(thisValue, zdt.plusDays(days.asLong))
    }

    @JSMethod("setFullYear", 3)
    fun setFullYear(thisValue: JSValue, arguments: JSArguments): JSValue {
        val zdt = thisTimeValue(thisValue, "setFullYear") ?: ZonedDateTime.ofInstant(Instant.EPOCH, Operations.defaultZone)
        expect(thisValue is JSDateObject)
        val year = Operations.toNumber(arguments.argument(0))
        val month = if (arguments.size > 1) Operations.toNumber(arguments[1]) else getMonth(thisValue, emptyList())
        val date = if (arguments.size > 2) Operations.toNumber(arguments[2]) else getDate(thisValue, emptyList())
        ifAnyNotFinite(thisValue, year, month, date) { return it }

        return dateValueSetHelper(thisValue, zdt.withYear(year.asInt).withMonth(month.asInt).withDayOfMonth(date.asInt))
    }

    @JSMethod("setHours", 4)
    fun setHours(thisValue: JSValue, arguments: JSArguments): JSValue {
        val zdt = thisTimeValue(thisValue, "setHours") ?: ZonedDateTime.ofInstant(Instant.EPOCH, Operations.defaultZone)
        expect(thisValue is JSDateObject)
        val hour = Operations.toNumber(arguments.argument(0))
        val minute = if (arguments.size > 1) Operations.toNumber(arguments[1]) else getMinutes(thisValue, emptyList())
        val second = if (arguments.size > 2) Operations.toNumber(arguments[2]) else getSeconds(thisValue, emptyList())
        val milli = if (arguments.size > 3) Operations.toNumber(arguments[3]) else getMilliseconds(thisValue, emptyList())
        ifAnyNotFinite(thisValue, minute, second, milli) { return it }

        return dateValueSetHelper(
            thisValue,
            zdt.withHour(hour.asInt).withMinute(minute.asInt).withSecond(second.asInt).withNano(milli.asInt * 1_000_000),
        )
    }

    @JSMethod("setMilliseconds", 1)
    fun setMilliseconds(thisValue: JSValue, arguments: JSArguments): JSValue {
        val zdt = thisTimeValue(thisValue, "setMilliseconds") ?: ZonedDateTime.ofInstant(Instant.EPOCH, Operations.defaultZone)
        expect(thisValue is JSDateObject)
        val ms = Operations.toNumber(arguments.argument(0))
        ifAnyNotFinite(thisValue, ms) { return it }

        return dateValueSetHelper(thisValue, zdt.withNano(ms.asInt))
    }

    @JSMethod("setMinutes", 3)
    fun setMinutes(thisValue: JSValue, arguments: JSArguments): JSValue {
        val zdt = thisTimeValue(thisValue, "setMinutes") ?: ZonedDateTime.ofInstant(Instant.EPOCH, Operations.defaultZone)
        expect(thisValue is JSDateObject)
        val minute = Operations.toNumber(arguments.argument(0))
        val second = if (arguments.size > 1) Operations.toNumber(arguments[1]) else getSeconds(thisValue, emptyList())
        val milli = if (arguments.size > 2) Operations.toNumber(arguments[2]) else getMilliseconds(thisValue, emptyList())
        ifAnyNotFinite(thisValue, minute, second, milli) { return it }

        return dateValueSetHelper(
            thisValue,
            zdt.withMinute(minute.asInt).withSecond(second.asInt).withNano(milli.asInt * 1_000_000)
        )
    }

    @JSMethod("setMonth", 2)
    fun setMonth(thisValue: JSValue, arguments: JSArguments): JSValue {
        val zdt = thisTimeValue(thisValue, "setMonth") ?: ZonedDateTime.ofInstant(Instant.EPOCH, Operations.defaultZone)
        expect(thisValue is JSDateObject)
        val month = Operations.toNumber(arguments.argument(0))
        val date = if (arguments.size > 1) Operations.toNumber(arguments[1]) else getDate(thisValue, emptyList())
        ifAnyNotFinite(thisValue, month, date) { return it }

        return dateValueSetHelper(thisValue, zdt.withMonth(month.asInt).withDayOfMonth(date.asInt))
    }

    @JSMethod("setSeconds", 2)
    fun setSeconds(thisValue: JSValue, arguments: JSArguments): JSValue {
        val zdt = thisTimeValue(thisValue, "setSeconds") ?: ZonedDateTime.ofInstant(Instant.EPOCH, Operations.defaultZone)
        expect(thisValue is JSDateObject)
        val second = Operations.toNumber(arguments.argument(0))
        val milli = if (arguments.size > 1) Operations.toNumber(arguments[1]) else getMilliseconds(thisValue, emptyList())
        ifAnyNotFinite(thisValue, second, milli) { return it }

        return dateValueSetHelper(
            thisValue,
            zdt.withSecond(second.asInt).withNano(milli.asInt * 1_000_000)
        )
    }

    @JSMethod("setTime", 2)
    fun setTime(thisValue: JSValue, arguments: JSArguments): JSValue {
        thisTimeValue(thisValue, "setTime")
        expect(thisValue is JSDateObject)
        val time = Operations.toNumber(arguments.argument(0))
        ifAnyNotFinite(thisValue, time) { return it }

        val tv = ZonedDateTime.ofInstant(Instant.ofEpochMilli(time.asLong), Operations.defaultZone)
        return dateValueSetHelper(thisValue, tv)
    }

    @JSMethod("setUTCDate", 1)
    fun setUTCDate(thisValue: JSValue, arguments: JSArguments): JSValue {
        thisUTCTimeValue(thisValue, "setUTCDate")
        expect(thisValue is JSDateObject)
        val date = Operations.toNumber(arguments.argument(0))
        ifAnyNotFinite(thisValue, date) { return it }
        return dateValueSetHelper(thisValue, ZonedDateTime.ofInstant(Instant.ofEpochMilli(date.asLong), ZoneOffset.UTC))
    }

    @JSMethod("setUTCFullYear", 3)
    fun setUTCFullYear(thisValue: JSValue, arguments: JSArguments): JSValue {
        val zdt = thisUTCTimeValue(thisValue, "setUTCFullYear") ?: ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC)
        expect(thisValue is JSDateObject)
        val year = Operations.toNumber(arguments.argument(0))
        val month = if (arguments.size > 1) Operations.toNumber(arguments[1]) else getMonth(thisValue, emptyList())
        val date = if (arguments.size > 2) Operations.toNumber(arguments[2]) else getDate(thisValue, emptyList())
        ifAnyNotFinite(thisValue, year, month, date) { return it }

        return dateValueSetHelper(thisValue, zdt.withYear(year.asInt).withMonth(month.asInt).withDayOfMonth(date.asInt))
    }

    @JSMethod("setUTCHours", 4)
    fun setUTCHours(thisValue: JSValue, arguments: JSArguments): JSValue {
        val zdt = thisUTCTimeValue(thisValue, "setUTCHours") ?: ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC)
        expect(thisValue is JSDateObject)
        val hour = Operations.toNumber(arguments.argument(0))
        val minute = if (arguments.size > 1) Operations.toNumber(arguments[1]) else getMinutes(thisValue, emptyList())
        val second = if (arguments.size > 2) Operations.toNumber(arguments[2]) else getSeconds(thisValue, emptyList())
        val milli = if (arguments.size > 3) Operations.toNumber(arguments[3]) else getMilliseconds(thisValue, emptyList())
        ifAnyNotFinite(thisValue, hour, minute, second, milli) { return it }

        return dateValueSetHelper(
            thisValue,
            zdt.withHour(hour.asInt).withMinute(minute.asInt).withSecond(second.asInt).withNano(milli.asInt * 1_000_000),
        )
    }

    @JSMethod("setUTCMilliseconds", 1)
    fun setUTCMilliseconds(thisValue: JSValue, arguments: JSArguments): JSValue {
        val zdt = thisUTCTimeValue(thisValue, "setUTCMilliseconds") ?: ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC)
        expect(thisValue is JSDateObject)
        val ms = Operations.toNumber(arguments.argument(0))
        ifAnyNotFinite(thisValue, ms) { return it }

        return dateValueSetHelper(thisValue, zdt.withNano(ms.asInt))
    }

    @JSMethod("setUTCMinutes", 3)
    fun setUTCMinutes(thisValue: JSValue, arguments: JSArguments): JSValue {
        val zdt = thisUTCTimeValue(thisValue, "setUTCMinutes") ?: ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC)
        expect(thisValue is JSDateObject)
        val minute = Operations.toNumber(arguments.argument(0))
        val second = if (arguments.size > 1) Operations.toNumber(arguments[1]) else getSeconds(thisValue, emptyList())
        val milli = if (arguments.size > 2) Operations.toNumber(arguments[2]) else getMilliseconds(thisValue, emptyList())
        ifAnyNotFinite(thisValue, minute, second, milli) { return it }

        return dateValueSetHelper(
            thisValue,
            zdt.withMinute(minute.asInt).withSecond(second.asInt).withNano(milli.asInt * 1_000_000)
        )
    }

    @JSMethod("setUTCMonth", 2)
    fun setUTCMonth(thisValue: JSValue, arguments: JSArguments): JSValue {
        val zdt = thisUTCTimeValue(thisValue, "setUTCMonth") ?: ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC)
        expect(thisValue is JSDateObject)
        val month = Operations.toNumber(arguments.argument(0))
        val date = if (arguments.size > 1) Operations.toNumber(arguments[1]) else getDate(thisValue, emptyList())
        ifAnyNotFinite(thisValue, month, date) { return it }

        return dateValueSetHelper(thisValue, zdt.withMonth(month.asInt).withDayOfMonth(date.asInt))
    }

    @JSMethod("setUTCSeconds", 2)
    fun setUTCSeconds(thisValue: JSValue, arguments: JSArguments): JSValue {
        val zdt = thisUTCTimeValue(thisValue, "setUTCSeconds") ?: ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC)
        expect(thisValue is JSDateObject)
        val second = Operations.toNumber(arguments.argument(0))
        val milli = if (arguments.size > 1) Operations.toNumber(arguments[1]) else getMilliseconds(thisValue, emptyList())
        ifAnyNotFinite(thisValue, second, milli) { return it }

        return dateValueSetHelper(
            thisValue,
            zdt.withSecond(second.asInt).withNano(milli.asInt * 1_000_000)
        )
    }

    @JSMethod("toDateString", 0)
    fun toDateString(thisValue: JSValue, arguments: JSArguments): JSValue {
        val zdt = thisTimeValue(thisValue, "toDateString") ?: return "Invalid Date".toValue()
        return Operations.dateString(zdt).toValue()
    }

    @JSMethod("toISOString", 0)
    fun toISOString(thisValue: JSValue, arguments: JSArguments): JSValue {
        var ztd = thisTimeValue(thisValue, "toISOString")?.toOffsetDateTime()?.atZoneSameInstant(ZoneOffset.UTC)
            ?: Errors.TODO("Date.prototype.toISOString").throwTypeError()
        val year = ztd.year
        if (year < 0) {
            ztd = ztd.withYear(-year)
        }

        val sign = when {
            year < 0 -> "-"
            year > 9999 -> "+"
            else -> ""
        }

        return (sign + ztd.format(JSDateCtor.dateTimeStringFormatter)).toValue()
    }

    @JSMethod("toJSON", 1)
    fun toJSON(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(thisValue)
        val tv = Operations.toPrimitive(obj, Operations.ToPrimitiveHint.AsNumber)
        if (tv is JSNumber && !tv.isFinite)
            return JSNull
        return Operations.invoke(obj, "toISOString".toValue())
    }

    @JSMethod("toString", 0, "CeW")
    fun toString(thisValue: JSValue, arguments: JSArguments): JSValue {
        return Operations.toDateString(thisTimeValue(thisValue, "toString") ?: return "Invalid Date".toValue()).toValue()
    }

    @JSMethod("toTimeString", 0)
    fun toTimeString(thisValue: JSValue, arguments: JSArguments): JSValue {
        val zdt = thisTimeValue(thisValue, "toTimeString") ?: return "Invalid Date".toValue()
        return (Operations.timeString(zdt) + Operations.timeZoneString(zdt)).toValue()
    }

    @JSMethod("toUTCString", 0)
    fun toUTCString(thisValue: JSValue, arguments: JSArguments): JSValue {
        val zdt = thisTimeValue(thisValue, "toUTCString") ?: return "Invalid Date".toValue()
        val weekday = zdt.dayOfWeek.getDisplayName(TextStyle.SHORT, Operations.defaultLocale)
        val month = zdt.month.getDisplayName(TextStyle.SHORT, Operations.defaultLocale)
        val day = "%02d".format(zdt.dayOfMonth)
        val yearSign = if (zdt.year >= 0) "" else "-"
        val paddedYear = zdt.year.toString().padStart(4, '0')

        return "$weekday, $day $month $yearSign$paddedYear ${Operations.timeString(zdt)}".toValue()
    }

    @JSMethod("valueOf", 0)
    fun valueOf(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisTimeValue(thisValue, "valueOf")?.toInstant()?.toEpochMilli()?.toValue() ?: JSNumber.NaN
    }

    @JSMethod("@@toPrimitive", 0, "Cew")
    fun `@@toPrimitive`(thisValue: JSValue, arguments: JSArguments): JSValue {
        if (thisValue !is JSObject)
            Errors.IncompatibleMethodCall("Date.prototype[Symbol.toPrimitive]").throwTypeError()

        val hint = Operations.toString(arguments.argument(0)).string

        return when (hint) {
            "string", "default" -> Operations.ordinaryToPrimitive(thisValue, Operations.ToPrimitiveHint.AsString)
            "number" -> Operations.ordinaryToPrimitive(thisValue, Operations.ToPrimitiveHint.AsNumber)
            else -> Errors.InvalidToPrimitiveHint(hint).throwTypeError()
        }
    }

    private fun dateValueSetHelper(dateObj: JSDateObject, zdt: ZonedDateTime): JSValue {
        return if (Operations.timeClip(zdt) == null) {
            dateObj.dateValue = null
            JSNumber.NaN
        } else {
            dateObj.dateValue = zdt
            zdt.toInstant().toEpochMilli().toValue()
        }
    }

    private inline fun ifAnyNotFinite(dateObj: JSDateObject, vararg values: JSValue, returner: (JSValue) -> Unit) {
        if (values.any { !it.isFinite }) {
            dateObj.dateValue = null
            returner(JSNumber.NaN)
        }
    }

    companion object {
        private val isoDTF = DateTimeFormatter.ofPattern("YYYY-MM-DD'T'HH:mm:ss.AAA'Z'")
        private val isoExtendedDTF = DateTimeFormatter.ofPattern("yyyyyy-MM-DD'T'HH:mm:ss.AAA'Z'")

        private fun thisTimeValue(value: JSValue, method: String): ZonedDateTime? {
            if (value !is JSDateObject)
                Errors.IncompatibleMethodCall("Date.prototype.$method").throwTypeError()
            return value.dateValue
        }

        private fun thisUTCTimeValue(value: JSValue, method: String): ZonedDateTime? {
            if (value !is JSDateObject)
                Errors.IncompatibleMethodCall("Date.prototype.$method").throwTypeError()
            return value.dateValue?.let {
                it.plusSeconds(it.offset.totalSeconds.toLong())
            }
        }

        fun create(realm: Realm) = JSDateProto(realm).initialize()
    }
}
