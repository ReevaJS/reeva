package me.mattco.reeva.runtime.builtins

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.SlotName
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSNull
import me.mattco.reeva.runtime.primitives.JSNumber
import me.mattco.reeva.utils.*
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@Suppress("UNUSED_PARAMETER")
class JSDateProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("constructor", realm.dateCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineNativeFunction("getDate", 0, ::getDate)
        defineNativeFunction("getDay", 0, ::getDay)
        defineNativeFunction("getFullYear", 0, ::getFullYear)
        defineNativeFunction("getHours", 0, ::getHours)
        defineNativeFunction("getMilliseconds", 0, ::getMilliseconds)
        defineNativeFunction("getMinutes", 0, ::getMinutes)
        defineNativeFunction("getMonth", 0, ::getMonth)
        defineNativeFunction("getSeconds", 0, ::getSeconds)
        defineNativeFunction("getTime", 0, ::getTime)
        defineNativeFunction("getTimezoneOffset", 0, ::getTimezoneOffset)
        defineNativeFunction("getUTCDate", 0, ::getUTCDate)
        defineNativeFunction("getUTCFullYear", 0, ::getUTCFullYear)
        defineNativeFunction("getUTCHours", 0, ::getUTCHours)
        defineNativeFunction("getUTCMilliseconds", 0, ::getUTCMilliseconds)
        defineNativeFunction("getUTCMinutes", 0, ::getUTCMinutes)
        defineNativeFunction("getUTCMonth", 0, ::getUTCMonth)
        defineNativeFunction("getUTCSeconds", 0, ::getUTCSeconds)
        defineNativeFunction("getUTCTime", 0, ::getUTCTime)
        defineNativeFunction("setDate", 1, ::setDate)
        defineNativeFunction("setFullYear", 3, ::setFullYear)
        defineNativeFunction("setHours", 4, ::setHours)
        defineNativeFunction("setMilliseconds", 1, ::setMilliseconds)
        defineNativeFunction("setMonth", 2, ::setMonth)
        defineNativeFunction("setSeconds", 2, ::setSeconds)
        defineNativeFunction("setTime", 2, ::setTime)
        defineNativeFunction("setUTCDate", 1, ::setUTCDate)
        defineNativeFunction("setUTCFullYear", 3, ::setUTCFullYear)
        defineNativeFunction("setUTCHours", 4, ::setUTCHours)
        defineNativeFunction("setUTCMilliseconds", 1, ::setUTCMilliseconds)
        defineNativeFunction("setUTCMinutes", 3, ::setUTCMinutes)
        defineNativeFunction("setUTCMonth", 2, ::setUTCMonth)
        defineNativeFunction("setUTCSeconds", 2, ::setUTCSeconds)
        defineNativeFunction("toDateString", 0, ::toDateString)
        defineNativeFunction("toISOString", 0, ::toISOString)
        defineNativeFunction("toJSON", 1, ::toJSON)
        defineNativeFunction("toString", 0, ::toString)
        defineNativeFunction("toTimeString", 0, ::toTimeString)
        defineNativeFunction("toUTCString", 0, ::toUTCString)
        defineNativeFunction("valueOf", 0, ::valueOf)
        defineNativeFunction(Realm.`@@toPrimitive`.key(), 0, function = ::`@@toPrimitive`)
    }

    fun getDate(arguments: JSArguments): JSValue {
        return thisTimeValue(arguments.thisValue, "getDate")?.dayOfMonth?.toValue() ?: return JSNumber.NaN
    }

    fun getDay(arguments: JSArguments): JSValue {
        return thisTimeValue(arguments.thisValue, "getDay")?.dayOfWeek?.value?.minus(1)?.toValue() ?: return JSNumber.NaN
    }

    fun getFullYear(arguments: JSArguments): JSValue {
        return thisTimeValue(arguments.thisValue, "getFullYear")?.year?.toValue() ?: return JSNumber.NaN
    }

    fun getHours(arguments: JSArguments): JSValue {
        return thisTimeValue(arguments.thisValue, "getHours")?.hour?.toValue() ?: return JSNumber.NaN
    }

    fun getMilliseconds(arguments: JSArguments): JSValue {
        return thisTimeValue(arguments.thisValue, "getMilliseconds")?.nano?.div(1_000_000L)?.toValue() ?: return JSNumber.NaN
    }

    fun getMinutes(arguments: JSArguments): JSValue {
        return thisTimeValue(arguments.thisValue, "getMinutes")?.minute?.toValue() ?: return JSNumber.NaN
    }

    fun getMonth(arguments: JSArguments): JSValue {
        return thisTimeValue(arguments.thisValue, "getMonth")?.monthValue?.toValue() ?: return JSNumber.NaN
    }

    fun getSeconds(arguments: JSArguments): JSValue {
        return thisTimeValue(arguments.thisValue, "getSeconds")?.second?.toValue() ?: return JSNumber.NaN
    }

    fun getTime(arguments: JSArguments): JSValue {
        return thisTimeValue(arguments.thisValue, "getTime")?.toInstant()?.toEpochMilli()?.toValue() ?: JSNumber.NaN
    }

    fun getTimezoneOffset(arguments: JSArguments): JSValue {
        return thisTimeValue(arguments.thisValue, "getTimezoneOffset")?.offset?.totalSeconds?.toValue() ?: return JSNumber.NaN
    }

    fun getUTCDate(arguments: JSArguments): JSValue {
        return thisUTCTimeValue(arguments.thisValue, "getUTCDate")?.dayOfMonth?.toValue() ?: return JSNumber.NaN
    }

    fun getUTCDay(arguments: JSArguments): JSValue {
        return thisUTCTimeValue(arguments.thisValue, "getUTCDay")?.dayOfWeek?.value?.minus(1)?.toValue() ?: return JSNumber.NaN
    }

    fun getUTCFullYear(arguments: JSArguments): JSValue {
        return thisUTCTimeValue(arguments.thisValue, "getUTCFullYear")?.year?.toValue() ?: return JSNumber.NaN
    }

    fun getUTCHours(arguments: JSArguments): JSValue {
        return thisUTCTimeValue(arguments.thisValue, "getUTCHours")?.hour?.toValue() ?: return JSNumber.NaN
    }

    fun getUTCMilliseconds(arguments: JSArguments): JSValue {
        return thisUTCTimeValue(arguments.thisValue, "getUTCMilliseconds")?.nano?.div(1_000_000L)?.toValue() ?: return JSNumber.NaN
    }

    fun getUTCMinutes(arguments: JSArguments): JSValue {
        return thisUTCTimeValue(arguments.thisValue, "getUTCMinutes")?.minute?.toValue() ?: return JSNumber.NaN
    }

    fun getUTCMonth(arguments: JSArguments): JSValue {
        return thisUTCTimeValue(arguments.thisValue, "getUTCMonth")?.monthValue?.toValue() ?: return JSNumber.NaN
    }

    fun getUTCSeconds(arguments: JSArguments): JSValue {
        return thisUTCTimeValue(arguments.thisValue, "getUTCSeconds")?.second?.toValue() ?: return JSNumber.NaN
    }

    fun getUTCTime(arguments: JSArguments): JSValue {
        return thisUTCTimeValue(arguments.thisValue, "getUTCTime")?.toInstant()?.toEpochMilli()?.toValue() ?: return JSNumber.NaN
    }

    fun setDate(arguments: JSArguments): JSValue {
        val zdt = thisTimeValue(arguments.thisValue, "setDate") ?: return JSNumber.NaN
        val days = Operations.toNumber(arguments.argument(0))
        ifAnyNotFinite(arguments.thisValue, days) { return it }
        return dateValueSetHelper(arguments.thisValue, zdt.plusDays(days.asLong))
    }

    fun setFullYear(arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        val zdt = thisTimeValue(thisValue, "setFullYear") ?: ZonedDateTime.ofInstant(Instant.EPOCH, Operations.defaultZone)
        expect(thisValue is JSDateObject)
        val year = Operations.toNumber(arguments.argument(0))
        val month = if (arguments.size > 1) Operations.toNumber(arguments[1]) else getMonth(arguments)
        val date = if (arguments.size > 2) Operations.toNumber(arguments[2]) else getDate(arguments)
        ifAnyNotFinite(thisValue, year, month, date) { return it }

        return dateValueSetHelper(thisValue, zdt.withYear(year.asInt).withMonth(month.asInt).withDayOfMonth(date.asInt))
    }

    fun setHours(arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        val zdt = thisTimeValue(thisValue, "setHours") ?: ZonedDateTime.ofInstant(Instant.EPOCH, Operations.defaultZone)
        expect(thisValue is JSDateObject)
        val hour = Operations.toNumber(arguments.argument(0))
        val minute = if (arguments.size > 1) Operations.toNumber(arguments[1]) else getMinutes(arguments)
        val second = if (arguments.size > 2) Operations.toNumber(arguments[2]) else getSeconds(arguments)
        val milli = if (arguments.size > 3) Operations.toNumber(arguments[3]) else getMilliseconds(arguments)
        ifAnyNotFinite(thisValue, minute, second, milli) { return it }

        return dateValueSetHelper(
            thisValue,
            zdt.withHour(hour.asInt).withMinute(minute.asInt).withSecond(second.asInt).withNano(milli.asInt * 1_000_000),
        )
    }

    fun setMilliseconds(arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        val zdt = thisTimeValue(thisValue, "setMilliseconds") ?: ZonedDateTime.ofInstant(Instant.EPOCH, Operations.defaultZone)
        expect(thisValue is JSDateObject)
        val ms = Operations.toNumber(arguments.argument(0))
        ifAnyNotFinite(thisValue, ms) { return it }

        return dateValueSetHelper(thisValue, zdt.withNano(ms.asInt))
    }

    fun setMinutes(arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        val zdt = thisTimeValue(thisValue, "setMinutes") ?: ZonedDateTime.ofInstant(Instant.EPOCH, Operations.defaultZone)
        expect(thisValue is JSDateObject)
        val minute = Operations.toNumber(arguments.argument(0))
        val second = if (arguments.size > 1) Operations.toNumber(arguments[1]) else getSeconds(arguments)
        val milli = if (arguments.size > 2) Operations.toNumber(arguments[2]) else getMilliseconds(arguments)
        ifAnyNotFinite(thisValue, minute, second, milli) { return it }

        return dateValueSetHelper(
            thisValue,
            zdt.withMinute(minute.asInt).withSecond(second.asInt).withNano(milli.asInt * 1_000_000)
        )
    }

    fun setMonth(arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        val zdt = thisTimeValue(thisValue, "setMonth") ?: ZonedDateTime.ofInstant(Instant.EPOCH, Operations.defaultZone)
        expect(thisValue is JSDateObject)
        val month = Operations.toNumber(arguments.argument(0))
        val date = if (arguments.size > 1) Operations.toNumber(arguments[1]) else getDate(arguments)
        ifAnyNotFinite(thisValue, month, date) { return it }

        return dateValueSetHelper(thisValue, zdt.withMonth(month.asInt).withDayOfMonth(date.asInt))
    }

    fun setSeconds(arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        val zdt = thisTimeValue(thisValue, "setSeconds") ?: ZonedDateTime.ofInstant(Instant.EPOCH, Operations.defaultZone)
        expect(thisValue is JSDateObject)
        val second = Operations.toNumber(arguments.argument(0))
        val milli = if (arguments.size > 1) Operations.toNumber(arguments[1]) else getMilliseconds(arguments)
        ifAnyNotFinite(thisValue, second, milli) { return it }

        return dateValueSetHelper(
            thisValue,
            zdt.withSecond(second.asInt).withNano(milli.asInt * 1_000_000)
        )
    }

    fun setTime(arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        thisTimeValue(thisValue, "setTime")
        expect(thisValue is JSDateObject)
        val time = Operations.toNumber(arguments.argument(0))
        ifAnyNotFinite(thisValue, time) { return it }

        val tv = ZonedDateTime.ofInstant(Instant.ofEpochMilli(time.asLong), Operations.defaultZone)
        return dateValueSetHelper(thisValue, tv)
    }

    fun setUTCDate(arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        thisUTCTimeValue(thisValue, "setUTCDate")
        expect(thisValue is JSDateObject)
        val date = Operations.toNumber(arguments.argument(0))
        ifAnyNotFinite(thisValue, date) { return it }
        return dateValueSetHelper(thisValue, ZonedDateTime.ofInstant(Instant.ofEpochMilli(date.asLong), ZoneOffset.UTC))
    }

    fun setUTCFullYear(arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        val zdt = thisUTCTimeValue(thisValue, "setUTCFullYear") ?: ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC)
        expect(thisValue is JSDateObject)
        val year = Operations.toNumber(arguments.argument(0))
        val month = if (arguments.size > 1) Operations.toNumber(arguments[1]) else getMonth(arguments)
        val date = if (arguments.size > 2) Operations.toNumber(arguments[2]) else getDate(arguments)
        ifAnyNotFinite(thisValue, year, month, date) { return it }

        return dateValueSetHelper(thisValue, zdt.withYear(year.asInt).withMonth(month.asInt).withDayOfMonth(date.asInt))
    }

    fun setUTCHours(arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        val zdt = thisUTCTimeValue(thisValue, "setUTCHours") ?: ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC)
        expect(thisValue is JSDateObject)
        val hour = Operations.toNumber(arguments.argument(0))
        val minute = if (arguments.size > 1) Operations.toNumber(arguments[1]) else getMinutes(arguments)
        val second = if (arguments.size > 2) Operations.toNumber(arguments[2]) else getSeconds(arguments)
        val milli = if (arguments.size > 3) Operations.toNumber(arguments[3]) else getMilliseconds(arguments)
        ifAnyNotFinite(thisValue, hour, minute, second, milli) { return it }

        return dateValueSetHelper(
            thisValue,
            zdt.withHour(hour.asInt).withMinute(minute.asInt).withSecond(second.asInt).withNano(milli.asInt * 1_000_000),
        )
    }

    fun setUTCMilliseconds(arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        val zdt = thisUTCTimeValue(thisValue, "setUTCMilliseconds") ?: ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC)
        expect(thisValue is JSDateObject)
        val ms = Operations.toNumber(arguments.argument(0))
        ifAnyNotFinite(thisValue, ms) { return it }

        return dateValueSetHelper(thisValue, zdt.withNano(ms.asInt))
    }

    fun setUTCMinutes(arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        val zdt = thisUTCTimeValue(thisValue, "setUTCMinutes") ?: ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC)
        expect(thisValue is JSDateObject)
        val minute = Operations.toNumber(arguments.argument(0))
        val second = if (arguments.size > 1) Operations.toNumber(arguments[1]) else getSeconds(arguments)
        val milli = if (arguments.size > 2) Operations.toNumber(arguments[2]) else getMilliseconds(arguments)
        ifAnyNotFinite(thisValue, minute, second, milli) { return it }

        return dateValueSetHelper(
            thisValue,
            zdt.withMinute(minute.asInt).withSecond(second.asInt).withNano(milli.asInt * 1_000_000)
        )
    }

    fun setUTCMonth(arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        val zdt = thisUTCTimeValue(thisValue, "setUTCMonth") ?: ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC)
        expect(thisValue is JSDateObject)
        val month = Operations.toNumber(arguments.argument(0))
        val date = if (arguments.size > 1) Operations.toNumber(arguments[1]) else getDate(arguments)
        ifAnyNotFinite(thisValue, month, date) { return it }

        return dateValueSetHelper(thisValue, zdt.withMonth(month.asInt).withDayOfMonth(date.asInt))
    }

    fun setUTCSeconds(arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        val zdt = thisUTCTimeValue(thisValue, "setUTCSeconds") ?: ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC)
        expect(thisValue is JSDateObject)
        val second = Operations.toNumber(arguments.argument(0))
        val milli = if (arguments.size > 1) Operations.toNumber(arguments[1]) else getMilliseconds(arguments)
        ifAnyNotFinite(thisValue, second, milli) { return it }

        return dateValueSetHelper(
            thisValue,
            zdt.withSecond(second.asInt).withNano(milli.asInt * 1_000_000)
        )
    }

    fun toDateString(arguments: JSArguments): JSValue {
        val zdt = thisTimeValue(arguments.thisValue, "toDateString") ?: return "Invalid Date".toValue()
        return Operations.dateString(zdt).toValue()
    }

    fun toISOString(arguments: JSArguments): JSValue {
        var ztd = thisTimeValue(arguments.thisValue, "toISOString")?.toOffsetDateTime()?.atZoneSameInstant(ZoneOffset.UTC)
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

    fun toJSON(arguments: JSArguments): JSValue {
        val obj = Operations.toObject(arguments.thisValue)
        val tv = Operations.toPrimitive(obj, Operations.ToPrimitiveHint.AsNumber)
        if (tv is JSNumber && !tv.isFinite)
            return JSNull
        return Operations.invoke(obj, "toISOString".toValue())
    }

    fun toString(arguments: JSArguments): JSValue {
        return Operations.toDateString(thisTimeValue(arguments.thisValue, "toString") ?: return "Invalid Date".toValue()).toValue()
    }

    fun toTimeString(arguments: JSArguments): JSValue {
        val zdt = thisTimeValue(arguments.thisValue, "toTimeString") ?: return "Invalid Date".toValue()
        return (Operations.timeString(zdt) + Operations.timeZoneString(zdt)).toValue()
    }

    fun toUTCString(arguments: JSArguments): JSValue {
        val zdt = thisTimeValue(arguments.thisValue, "toUTCString") ?: return "Invalid Date".toValue()
        val weekday = zdt.dayOfWeek.getDisplayName(TextStyle.SHORT, Operations.defaultLocale)
        val month = zdt.month.getDisplayName(TextStyle.SHORT, Operations.defaultLocale)
        val day = "%02d".format(zdt.dayOfMonth)
        val yearSign = if (zdt.year >= 0) "" else "-"
        val paddedYear = zdt.year.toString().padStart(4, '0')

        return "$weekday, $day $month $yearSign$paddedYear ${Operations.timeString(zdt)}".toValue()
    }

    fun valueOf(arguments: JSArguments): JSValue {
        return thisTimeValue(arguments.thisValue, "valueOf")?.toInstant()?.toEpochMilli()?.toValue() ?: JSNumber.NaN
    }

    fun `@@toPrimitive`(arguments: JSArguments): JSValue {
        if (arguments.thisValue !is JSObject)
            Errors.IncompatibleMethodCall("Date.prototype[Symbol.toPrimitive]").throwTypeError()

        val hint = Operations.toString(arguments.argument(0)).string

        return when (hint) {
            "string", "default" -> Operations.ordinaryToPrimitive(arguments.thisValue, Operations.ToPrimitiveHint.AsString)
            "number" -> Operations.ordinaryToPrimitive(arguments.thisValue, Operations.ToPrimitiveHint.AsNumber)
            else -> Errors.InvalidToPrimitiveHint(hint).throwTypeError()
        }
    }

    private fun dateValueSetHelper(dateObj: JSObject, zdt: ZonedDateTime): JSValue {
        return if (Operations.timeClip(zdt) == null) {
            dateObj.setSlot(SlotName.DateValue, null)
            JSNumber.NaN
        } else {
            dateObj.setSlot(SlotName.DateValue, zdt)
            zdt.toInstant().toEpochMilli().toValue()
        }
    }

    private inline fun ifAnyNotFinite(dateObj: JSObject, vararg values: JSValue, returner: (JSValue) -> Unit) {
        if (values.any { !it.isFinite }) {
            dateObj.setSlot(SlotName.DateValue, null)
            returner(JSNumber.NaN)
        }
    }

    companion object {
        private val isoDTF = DateTimeFormatter.ofPattern("YYYY-MM-DD'T'HH:mm:ss.AAA'Z'")
        private val isoExtendedDTF = DateTimeFormatter.ofPattern("yyyyyy-MM-DD'T'HH:mm:ss.AAA'Z'")

        @OptIn(ExperimentalContracts::class)
        private fun thisTimeValue(value: JSValue, method: String): ZonedDateTime? {
            contract {
                returns() implies (value is JSObject)
            }
            if (value !is JSObject || !value.hasSlot(SlotName.DateValue))
                Errors.IncompatibleMethodCall("Date.prototype.$method").throwTypeError()
            return value.getSlotAs(SlotName.DateValue)
        }

        private fun thisUTCTimeValue(value: JSValue, method: String): ZonedDateTime? {
            return thisTimeValue(value, method)?.let {
                it.plusSeconds(it.offset.totalSeconds.toLong())
            }
        }

        fun create(realm: Realm) = JSDateProto(realm).initialize()
    }
}
