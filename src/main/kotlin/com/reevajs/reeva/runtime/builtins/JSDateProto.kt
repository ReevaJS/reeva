package com.reevajs.reeva.runtime.builtins

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSArguments
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.SlotName
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSNull
import com.reevajs.reeva.runtime.primitives.JSNumber
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.expect
import com.reevajs.reeva.utils.key
import com.reevajs.reeva.utils.toValue
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

    fun getDate(realm: Realm, arguments: JSArguments): JSValue {
        return thisTimeValue(realm, arguments.thisValue, "getDate")?.dayOfMonth?.toValue() ?: return JSNumber.NaN
    }

    fun getDay(realm: Realm, arguments: JSArguments): JSValue {
        return thisTimeValue(realm, arguments.thisValue, "getDay")?.dayOfWeek?.value?.minus(1)?.toValue() ?: return JSNumber.NaN
    }

    fun getFullYear(realm: Realm, arguments: JSArguments): JSValue {
        return thisTimeValue(realm, arguments.thisValue, "getFullYear")?.year?.toValue() ?: return JSNumber.NaN
    }

    fun getHours(realm: Realm, arguments: JSArguments): JSValue {
        return thisTimeValue(realm, arguments.thisValue, "getHours")?.hour?.toValue() ?: return JSNumber.NaN
    }

    fun getMilliseconds(realm: Realm, arguments: JSArguments): JSValue {
        return thisTimeValue(realm, arguments.thisValue, "getMilliseconds")?.nano?.div(1_000_000L)?.toValue() ?: return JSNumber.NaN
    }

    fun getMinutes(realm: Realm, arguments: JSArguments): JSValue {
        return thisTimeValue(realm, arguments.thisValue, "getMinutes")?.minute?.toValue() ?: return JSNumber.NaN
    }

    fun getMonth(realm: Realm, arguments: JSArguments): JSValue {
        return thisTimeValue(realm, arguments.thisValue, "getMonth")?.monthValue?.toValue() ?: return JSNumber.NaN
    }

    fun getSeconds(realm: Realm, arguments: JSArguments): JSValue {
        return thisTimeValue(realm, arguments.thisValue, "getSeconds")?.second?.toValue() ?: return JSNumber.NaN
    }

    fun getTime(realm: Realm, arguments: JSArguments): JSValue {
        return thisTimeValue(realm, arguments.thisValue, "getTime")?.toInstant()?.toEpochMilli()?.toValue() ?: JSNumber.NaN
    }

    fun getTimezoneOffset(realm: Realm, arguments: JSArguments): JSValue {
        return thisTimeValue(realm, arguments.thisValue, "getTimezoneOffset")?.offset?.totalSeconds?.toValue() ?: return JSNumber.NaN
    }

    fun getUTCDate(realm: Realm, arguments: JSArguments): JSValue {
        return thisUTCTimeValue(realm, arguments.thisValue, "getUTCDate")?.dayOfMonth?.toValue() ?: return JSNumber.NaN
    }

    fun getUTCDay(realm: Realm, arguments: JSArguments): JSValue {
        return thisUTCTimeValue(realm, arguments.thisValue, "getUTCDay")?.dayOfWeek?.value?.minus(1)?.toValue() ?: return JSNumber.NaN
    }

    fun getUTCFullYear(realm: Realm, arguments: JSArguments): JSValue {
        return thisUTCTimeValue(realm, arguments.thisValue, "getUTCFullYear")?.year?.toValue() ?: return JSNumber.NaN
    }

    fun getUTCHours(realm: Realm, arguments: JSArguments): JSValue {
        return thisUTCTimeValue(realm, arguments.thisValue, "getUTCHours")?.hour?.toValue() ?: return JSNumber.NaN
    }

    fun getUTCMilliseconds(realm: Realm, arguments: JSArguments): JSValue {
        return thisUTCTimeValue(realm, arguments.thisValue, "getUTCMilliseconds")?.nano?.div(1_000_000L)?.toValue() ?: return JSNumber.NaN
    }

    fun getUTCMinutes(realm: Realm, arguments: JSArguments): JSValue {
        return thisUTCTimeValue(realm, arguments.thisValue, "getUTCMinutes")?.minute?.toValue() ?: return JSNumber.NaN
    }

    fun getUTCMonth(realm: Realm, arguments: JSArguments): JSValue {
        return thisUTCTimeValue(realm, arguments.thisValue, "getUTCMonth")?.monthValue?.toValue() ?: return JSNumber.NaN
    }

    fun getUTCSeconds(realm: Realm, arguments: JSArguments): JSValue {
        return thisUTCTimeValue(realm, arguments.thisValue, "getUTCSeconds")?.second?.toValue() ?: return JSNumber.NaN
    }

    fun getUTCTime(realm: Realm, arguments: JSArguments): JSValue {
        return thisUTCTimeValue(realm, arguments.thisValue, "getUTCTime")?.toInstant()?.toEpochMilli()?.toValue() ?: return JSNumber.NaN
    }

    fun setDate(realm: Realm, arguments: JSArguments): JSValue {
        val zdt = thisTimeValue(realm, arguments.thisValue, "setDate") ?: return JSNumber.NaN
        val days = Operations.toNumber(realm, arguments.argument(0))
        ifAnyNotFinite(arguments.thisValue, days) { return it }
        return dateValueSetHelper(arguments.thisValue, zdt.plusDays(days.asLong))
    }

    fun setFullYear(realm: Realm, arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        val zdt = thisTimeValue(realm, thisValue, "setFullYear") ?: ZonedDateTime.ofInstant(Instant.EPOCH, Operations.defaultZone)
        expect(thisValue is JSDateObject)
        val year = Operations.toNumber(realm, arguments.argument(0))
        val month = if (arguments.size > 1) Operations.toNumber(realm, arguments[1]) else getMonth(realm, arguments)
        val date = if (arguments.size > 2) Operations.toNumber(realm, arguments[2]) else getDate(realm, arguments)
        ifAnyNotFinite(thisValue, year, month, date) { return it }

        return dateValueSetHelper(thisValue, zdt.withYear(year.asInt).withMonth(month.asInt).withDayOfMonth(date.asInt))
    }

    fun setHours(realm: Realm, arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        val zdt = thisTimeValue(realm, thisValue, "setHours") ?: ZonedDateTime.ofInstant(Instant.EPOCH, Operations.defaultZone)
        expect(thisValue is JSDateObject)
        val hour = Operations.toNumber(realm, arguments.argument(0))
        val minute = if (arguments.size > 1) Operations.toNumber(realm, arguments[1]) else getMinutes(realm, arguments)
        val second = if (arguments.size > 2) Operations.toNumber(realm, arguments[2]) else getSeconds(realm, arguments)
        val milli = if (arguments.size > 3) Operations.toNumber(realm, arguments[3]) else getMilliseconds(realm, arguments)
        ifAnyNotFinite(thisValue, minute, second, milli) { return it }

        return dateValueSetHelper(
            thisValue,
            zdt.withHour(hour.asInt).withMinute(minute.asInt).withSecond(second.asInt).withNano(milli.asInt * 1_000_000),
        )
    }

    fun setMilliseconds(realm: Realm, arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        val zdt = thisTimeValue(realm, thisValue, "setMilliseconds") ?: ZonedDateTime.ofInstant(Instant.EPOCH, Operations.defaultZone)
        expect(thisValue is JSDateObject)
        val ms = Operations.toNumber(realm, arguments.argument(0))
        ifAnyNotFinite(thisValue, ms) { return it }

        return dateValueSetHelper(thisValue, zdt.withNano(ms.asInt))
    }

    fun setMinutes(realm: Realm, arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        val zdt = thisTimeValue(realm, thisValue, "setMinutes") ?: ZonedDateTime.ofInstant(Instant.EPOCH, Operations.defaultZone)
        expect(thisValue is JSDateObject)
        val minute = Operations.toNumber(realm, arguments.argument(0))
        val second = if (arguments.size > 1) Operations.toNumber(realm, arguments[1]) else getSeconds(realm, arguments)
        val milli = if (arguments.size > 2) Operations.toNumber(realm, arguments[2]) else getMilliseconds(realm, arguments)
        ifAnyNotFinite(thisValue, minute, second, milli) { return it }

        return dateValueSetHelper(
            thisValue,
            zdt.withMinute(minute.asInt).withSecond(second.asInt).withNano(milli.asInt * 1_000_000)
        )
    }

    fun setMonth(realm: Realm, arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        val zdt = thisTimeValue(realm, thisValue, "setMonth") ?: ZonedDateTime.ofInstant(Instant.EPOCH, Operations.defaultZone)
        expect(thisValue is JSDateObject)
        val month = Operations.toNumber(realm, arguments.argument(0))
        val date = if (arguments.size > 1) Operations.toNumber(realm, arguments[1]) else getDate(realm, arguments)
        ifAnyNotFinite(thisValue, month, date) { return it }

        return dateValueSetHelper(thisValue, zdt.withMonth(month.asInt).withDayOfMonth(date.asInt))
    }

    fun setSeconds(realm: Realm, arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        val zdt = thisTimeValue(realm, thisValue, "setSeconds") ?: ZonedDateTime.ofInstant(Instant.EPOCH, Operations.defaultZone)
        expect(thisValue is JSDateObject)
        val second = Operations.toNumber(realm, arguments.argument(0))
        val milli = if (arguments.size > 1) Operations.toNumber(realm, arguments[1]) else getMilliseconds(realm, arguments)
        ifAnyNotFinite(thisValue, second, milli) { return it }

        return dateValueSetHelper(
            thisValue,
            zdt.withSecond(second.asInt).withNano(milli.asInt * 1_000_000)
        )
    }

    fun setTime(realm: Realm, arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        thisTimeValue(realm, thisValue, "setTime")
        expect(thisValue is JSDateObject)
        val time = Operations.toNumber(realm, arguments.argument(0))
        ifAnyNotFinite(thisValue, time) { return it }

        val tv = ZonedDateTime.ofInstant(Instant.ofEpochMilli(time.asLong), Operations.defaultZone)
        return dateValueSetHelper(thisValue, tv)
    }

    fun setUTCDate(realm: Realm, arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        thisUTCTimeValue(realm, thisValue, "setUTCDate")
        expect(thisValue is JSDateObject)
        val date = Operations.toNumber(realm, arguments.argument(0))
        ifAnyNotFinite(thisValue, date) { return it }
        return dateValueSetHelper(thisValue, ZonedDateTime.ofInstant(Instant.ofEpochMilli(date.asLong), ZoneOffset.UTC))
    }

    fun setUTCFullYear(realm: Realm, arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        val zdt = thisUTCTimeValue(realm, thisValue, "setUTCFullYear") ?: ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC)
        expect(thisValue is JSDateObject)
        val year = Operations.toNumber(realm, arguments.argument(0))
        val month = if (arguments.size > 1) Operations.toNumber(realm, arguments[1]) else getMonth(realm, arguments)
        val date = if (arguments.size > 2) Operations.toNumber(realm, arguments[2]) else getDate(realm, arguments)
        ifAnyNotFinite(thisValue, year, month, date) { return it }

        return dateValueSetHelper(thisValue, zdt.withYear(year.asInt).withMonth(month.asInt).withDayOfMonth(date.asInt))
    }

    fun setUTCHours(realm: Realm, arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        val zdt = thisUTCTimeValue(realm, thisValue, "setUTCHours") ?: ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC)
        expect(thisValue is JSDateObject)
        val hour = Operations.toNumber(realm, arguments.argument(0))
        val minute = if (arguments.size > 1) Operations.toNumber(realm, arguments[1]) else getMinutes(realm, arguments)
        val second = if (arguments.size > 2) Operations.toNumber(realm, arguments[2]) else getSeconds(realm, arguments)
        val milli = if (arguments.size > 3) Operations.toNumber(realm, arguments[3]) else getMilliseconds(realm, arguments)
        ifAnyNotFinite(thisValue, hour, minute, second, milli) { return it }

        return dateValueSetHelper(
            thisValue,
            zdt.withHour(hour.asInt).withMinute(minute.asInt).withSecond(second.asInt).withNano(milli.asInt * 1_000_000),
        )
    }

    fun setUTCMilliseconds(realm: Realm, arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        val zdt = thisUTCTimeValue(realm, thisValue, "setUTCMilliseconds") ?: ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC)
        expect(thisValue is JSDateObject)
        val ms = Operations.toNumber(realm, arguments.argument(0))
        ifAnyNotFinite(thisValue, ms) { return it }

        return dateValueSetHelper(thisValue, zdt.withNano(ms.asInt))
    }

    fun setUTCMinutes(realm: Realm, arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        val zdt = thisUTCTimeValue(realm, thisValue, "setUTCMinutes") ?: ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC)
        expect(thisValue is JSDateObject)
        val minute = Operations.toNumber(realm, arguments.argument(0))
        val second = if (arguments.size > 1) Operations.toNumber(realm, arguments[1]) else getSeconds(realm, arguments)
        val milli = if (arguments.size > 2) Operations.toNumber(realm, arguments[2]) else getMilliseconds(realm, arguments)
        ifAnyNotFinite(thisValue, minute, second, milli) { return it }

        return dateValueSetHelper(
            thisValue,
            zdt.withMinute(minute.asInt).withSecond(second.asInt).withNano(milli.asInt * 1_000_000)
        )
    }

    fun setUTCMonth(realm: Realm, arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        val zdt = thisUTCTimeValue(realm, thisValue, "setUTCMonth") ?: ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC)
        expect(thisValue is JSDateObject)
        val month = Operations.toNumber(realm, arguments.argument(0))
        val date = if (arguments.size > 1) Operations.toNumber(realm, arguments[1]) else getDate(realm, arguments)
        ifAnyNotFinite(thisValue, month, date) { return it }

        return dateValueSetHelper(thisValue, zdt.withMonth(month.asInt).withDayOfMonth(date.asInt))
    }

    fun setUTCSeconds(realm: Realm, arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        val zdt = thisUTCTimeValue(realm, thisValue, "setUTCSeconds") ?: ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC)
        expect(thisValue is JSDateObject)
        val second = Operations.toNumber(realm, arguments.argument(0))
        val milli = if (arguments.size > 1) Operations.toNumber(realm, arguments[1]) else getMilliseconds(realm, arguments)
        ifAnyNotFinite(thisValue, second, milli) { return it }

        return dateValueSetHelper(
            thisValue,
            zdt.withSecond(second.asInt).withNano(milli.asInt * 1_000_000)
        )
    }

    fun toDateString(realm: Realm, arguments: JSArguments): JSValue {
        val zdt = thisTimeValue(realm, arguments.thisValue, "toDateString") ?: return "Invalid Date".toValue()
        return Operations.dateString(zdt).toValue()
    }

    fun toISOString(realm: Realm, arguments: JSArguments): JSValue {
        var ztd = thisTimeValue(realm, arguments.thisValue, "toISOString")?.toOffsetDateTime()?.atZoneSameInstant(ZoneOffset.UTC)
            ?: Errors.TODO("Date.prototype.toISOString").throwTypeError(realm)
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

    fun toJSON(realm: Realm, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(realm, arguments.thisValue)
        val tv = Operations.toPrimitive(realm, obj, Operations.ToPrimitiveHint.AsNumber)
        if (tv is JSNumber && !tv.isFinite)
            return JSNull
        return Operations.invoke(realm, obj, "toISOString".toValue())
    }

    fun toString(realm: Realm, arguments: JSArguments): JSValue {
        return Operations.toDateString(thisTimeValue(realm, arguments.thisValue, "toString") ?: return "Invalid Date".toValue()).toValue()
    }

    fun toTimeString(realm: Realm, arguments: JSArguments): JSValue {
        val zdt = thisTimeValue(realm, arguments.thisValue, "toTimeString") ?: return "Invalid Date".toValue()
        return (Operations.timeString(zdt) + Operations.timeZoneString(zdt)).toValue()
    }

    fun toUTCString(realm: Realm, arguments: JSArguments): JSValue {
        val zdt = thisTimeValue(realm, arguments.thisValue, "toUTCString") ?: return "Invalid Date".toValue()
        val weekday = zdt.dayOfWeek.getDisplayName(TextStyle.SHORT, Operations.defaultLocale)
        val month = zdt.month.getDisplayName(TextStyle.SHORT, Operations.defaultLocale)
        val day = "%02d".format(zdt.dayOfMonth)
        val yearSign = if (zdt.year >= 0) "" else "-"
        val paddedYear = zdt.year.toString().padStart(4, '0')

        return "$weekday, $day $month $yearSign$paddedYear ${Operations.timeString(zdt)}".toValue()
    }

    fun valueOf(realm: Realm, arguments: JSArguments): JSValue {
        return thisTimeValue(realm, arguments.thisValue, "valueOf")?.toInstant()?.toEpochMilli()?.toValue() ?: JSNumber.NaN
    }

    fun `@@toPrimitive`(realm: Realm, arguments: JSArguments): JSValue {
        if (arguments.thisValue !is JSObject)
            Errors.IncompatibleMethodCall("Date.prototype[Symbol.toPrimitive]").throwTypeError(realm)

        val hint = Operations.toString(realm, arguments.argument(0)).string

        return when (hint) {
            "string", "default" -> Operations.ordinaryToPrimitive(realm, arguments.thisValue, Operations.ToPrimitiveHint.AsString)
            "number" -> Operations.ordinaryToPrimitive(realm, arguments.thisValue, Operations.ToPrimitiveHint.AsNumber)
            else -> Errors.InvalidToPrimitiveHint(hint).throwTypeError(realm)
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
        private fun thisTimeValue(realm: Realm, value: JSValue, method: String): ZonedDateTime? {
            contract {
                returns() implies (value is JSObject)
            }
            if (value !is JSObject || !value.hasSlot(SlotName.DateValue))
                Errors.IncompatibleMethodCall("Date.prototype.$method").throwTypeError(realm)
            return value.getSlotAs(SlotName.DateValue)
        }

        private fun thisUTCTimeValue(realm: Realm, value: JSValue, method: String): ZonedDateTime? {
            return thisTimeValue(realm, value, method)?.let {
                it.plusSeconds(it.offset.totalSeconds.toLong())
            }
        }

        fun create(realm: Realm) = JSDateProto(realm).initialize()
    }
}
