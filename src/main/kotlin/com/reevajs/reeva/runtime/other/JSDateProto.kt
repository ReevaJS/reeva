package com.reevajs.reeva.runtime.other

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.JSNull
import com.reevajs.reeva.runtime.primitives.JSNumber
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.attrs
import com.reevajs.reeva.utils.expect
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
        defineBuiltin("getDate", 0, ::getDate)
        defineBuiltin("getDay", 0, ::getDay)
        defineBuiltin("getFullYear", 0, ::getFullYear)
        defineBuiltin("getHours", 0, ::getHours)
        defineBuiltin("getMilliseconds", 0, ::getMilliseconds)
        defineBuiltin("getMinutes", 0, ::getMinutes)
        defineBuiltin("getMonth", 0, ::getMonth)
        defineBuiltin("getSeconds", 0, ::getSeconds)
        defineBuiltin("getTime", 0, ::getTime)
        defineBuiltin("getTimezoneOffset", 0, ::getTimezoneOffset)
        defineBuiltin("getUTCDate", 0, ::getUTCDate)
        defineBuiltin("getUTCFullYear", 0, ::getUTCFullYear)
        defineBuiltin("getUTCHours", 0, ::getUTCHours)
        defineBuiltin("getUTCMilliseconds", 0, ::getUTCMilliseconds)
        defineBuiltin("getUTCMinutes", 0, ::getUTCMinutes)
        defineBuiltin("getUTCMonth", 0, ::getUTCMonth)
        defineBuiltin("getUTCSeconds", 0, ::getUTCSeconds)
        defineBuiltin("setDate", 1, ::setDate)
        defineBuiltin("setFullYear", 3, ::setFullYear)
        defineBuiltin("setHours", 4, ::setHours)
        defineBuiltin("setMilliseconds", 1, ::setMilliseconds)
        defineBuiltin("setMonth", 2, ::setMonth)
        defineBuiltin("setSeconds", 2, ::setSeconds)
        defineBuiltin("setTime", 2, ::setTime)
        defineBuiltin("setUTCDate", 1, ::setUTCDate)
        defineBuiltin("setUTCFullYear", 3, ::setUTCFullYear)
        defineBuiltin("setUTCHours", 4, ::setUTCHours)
        defineBuiltin("setUTCMilliseconds", 1, ::setUTCMilliseconds)
        defineBuiltin("setUTCMinutes", 3, ::setUTCMinutes)
        defineBuiltin("setUTCMonth", 2, ::setUTCMonth)
        defineBuiltin("setUTCSeconds", 2, ::setUTCSeconds)
        defineBuiltin("toDateString", 0, ::toDateString)
        defineBuiltin("toISOString", 0, ::toISOString)
        defineBuiltin("toJSON", 1, ::toJSON)
        defineBuiltin("toString", 0, ::toString)
        defineBuiltin("toTimeString", 0, ::toTimeString)
        defineBuiltin("toUTCString", 0, ::toUTCString)
        defineBuiltin("valueOf", 0, ::valueOf)
        defineBuiltin(Realm.WellKnownSymbols.toPrimitive, 0, ::symbolToPrimitive, attrs { +conf; -enum })
    }

    companion object {
        private val isoDTF = DateTimeFormatter.ofPattern("YYYY-MM-DD'T'HH:mm:ss.AAA'Z'")
        private val isoExtendedDTF = DateTimeFormatter.ofPattern("yyyyyy-MM-DD'T'HH:mm:ss.AAA'Z'")

        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSDateProto(realm).initialize()

        @OptIn(ExperimentalContracts::class)
        private fun thisTimeValue(value: JSValue, method: String): ZonedDateTime? {
            contract {
                returns() implies (value is JSObject)
            }
            if (value !is JSObject || Slot.DateValue !in value)
                Errors.IncompatibleMethodCall("Date.prototype.$method").throwTypeError()
            return value[Slot.DateValue]
        }

        private fun thisUTCTimeValue(value: JSValue, method: String): ZonedDateTime? {
            return thisTimeValue(value, method)?.let {
                it.plusSeconds(it.offset.totalSeconds.toLong())
            }
        }

        @ECMAImpl("21.4.4.2")
        @JvmStatic
        fun getDate(arguments: JSArguments): JSValue {
            return thisTimeValue(arguments.thisValue, "getDate")?.dayOfMonth?.toValue() ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.3")
        @JvmStatic
        fun getDay(arguments: JSArguments): JSValue {
            return thisTimeValue(arguments.thisValue, "getDay")?.dayOfWeek?.value?.minus(1)?.toValue()
                ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.4")
        @JvmStatic
        fun getFullYear(arguments: JSArguments): JSValue {
            return thisTimeValue(arguments.thisValue, "getFullYear")?.year?.toValue() ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.5")
        @JvmStatic
        fun getHours(arguments: JSArguments): JSValue {
            return thisTimeValue(arguments.thisValue, "getHours")?.hour?.toValue() ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.6")
        @JvmStatic
        fun getMilliseconds(arguments: JSArguments): JSValue {
            return thisTimeValue(arguments.thisValue, "getMilliseconds")?.nano?.div(1_000_000L)?.toValue()
                ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.7")
        @JvmStatic
        fun getMinutes(arguments: JSArguments): JSValue {
            return thisTimeValue(arguments.thisValue, "getMinutes")?.minute?.toValue() ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.8")
        @JvmStatic
        fun getMonth(arguments: JSArguments): JSValue {
            return thisTimeValue(arguments.thisValue, "getMonth")?.monthValue?.toValue() ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.9")
        @JvmStatic
        fun getSeconds(arguments: JSArguments): JSValue {
            return thisTimeValue(arguments.thisValue, "getSeconds")?.second?.toValue() ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.10")
        @JvmStatic
        fun getTime(arguments: JSArguments): JSValue {
            return thisTimeValue(arguments.thisValue, "getTime")?.toInstant()?.toEpochMilli()?.toValue()
                ?: JSNumber.NaN
        }

        @ECMAImpl("21.4.4.11")
        @JvmStatic
        fun getTimezoneOffset(arguments: JSArguments): JSValue {
            return thisTimeValue(arguments.thisValue, "getTimezoneOffset")?.offset?.totalSeconds?.toValue()
                ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.12")
        @JvmStatic
        fun getUTCDate(arguments: JSArguments): JSValue {
            return thisUTCTimeValue(arguments.thisValue, "getUTCDate")?.dayOfMonth?.toValue()
                ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.13")
        @JvmStatic
        fun getUTCDay(arguments: JSArguments): JSValue {
            return thisUTCTimeValue(arguments.thisValue, "getUTCDay")?.dayOfWeek?.value?.minus(1)?.toValue()
                ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.14")
        @JvmStatic
        fun getUTCFullYear(arguments: JSArguments): JSValue {
            return thisUTCTimeValue(arguments.thisValue, "getUTCFullYear")?.year?.toValue()
                ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.15")
        @JvmStatic
        fun getUTCHours(arguments: JSArguments): JSValue {
            return thisUTCTimeValue(arguments.thisValue, "getUTCHours")?.hour?.toValue() ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.16")
        @JvmStatic
        fun getUTCMilliseconds(arguments: JSArguments): JSValue {
            return thisUTCTimeValue(arguments.thisValue, "getUTCMilliseconds")?.nano?.div(1_000_000L)?.toValue()
                ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.17")
        @JvmStatic
        fun getUTCMinutes(arguments: JSArguments): JSValue {
            return thisUTCTimeValue(arguments.thisValue, "getUTCMinutes")?.minute?.toValue()
                ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.18")
        @JvmStatic
        fun getUTCMonth(arguments: JSArguments): JSValue {
            return thisUTCTimeValue(arguments.thisValue, "getUTCMonth")?.monthValue?.toValue()
                ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.19")
        @JvmStatic
        fun getUTCSeconds(arguments: JSArguments): JSValue {
            return thisUTCTimeValue(arguments.thisValue, "getUTCSeconds")?.second?.toValue()
                ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.20")
        @JvmStatic
        fun setDate(arguments: JSArguments): JSValue {
            val zdt = thisTimeValue(arguments.thisValue, "setDate") ?: return JSNumber.NaN
            val days = arguments.argument(0).toNumber()
            ifAnyNotFinite(arguments.thisValue, days) { return it }
            return dateValueSetHelper(arguments.thisValue, zdt.plusDays(days.asLong))
        }

        @ECMAImpl("21.4.4.21")
        @JvmStatic
        fun setFullYear(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            val zdt = thisTimeValue(thisValue, "setFullYear") ?: ZonedDateTime.ofInstant(
                Instant.EPOCH,
                AOs.defaultZone
            )
            expect(thisValue is JSDateObject)
            val year = arguments.argument(0).toNumber()
            val month = if (arguments.size > 1) arguments[1].toNumber() else getMonth(arguments)
            val date = if (arguments.size > 2) arguments[2].toNumber() else getDate(arguments)
            ifAnyNotFinite(thisValue, year, month, date) { return it }

            return dateValueSetHelper(
                thisValue,
                zdt.withYear(year.asInt).withMonth(month.asInt).withDayOfMonth(date.asInt)
            )
        }

        @ECMAImpl("21.4.4.22")
        @JvmStatic
        fun setHours(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            val zdt = thisTimeValue(thisValue, "setHours") ?: ZonedDateTime.ofInstant(
                Instant.EPOCH,
                AOs.defaultZone
            )
            expect(thisValue is JSDateObject)
            val hour = arguments.argument(0).toNumber()
            val minute = if (arguments.size > 1) arguments[1].toNumber() else getMinutes(arguments)
            val second = if (arguments.size > 2) arguments[2].toNumber() else getSeconds(arguments)
            val milli = if (arguments.size > 3) arguments[3].toNumber() else getMilliseconds(arguments)
            ifAnyNotFinite(thisValue, minute, second, milli) { return it }

            return dateValueSetHelper(
                thisValue,
                zdt.withHour(hour.asInt).withMinute(minute.asInt).withSecond(second.asInt)
                    .withNano(milli.asInt * 1_000_000),
            )
        }

        @ECMAImpl("21.4.4.23")
        @JvmStatic
        fun setMilliseconds(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            val zdt = thisTimeValue(thisValue, "setMilliseconds") ?: ZonedDateTime.ofInstant(
                Instant.EPOCH,
                AOs.defaultZone
            )
            expect(thisValue is JSDateObject)
            val ms = arguments.argument(0).toNumber()
            ifAnyNotFinite(thisValue, ms) { return it }

            return dateValueSetHelper(thisValue, zdt.withNano(ms.asInt))
        }

        @ECMAImpl("21.4.4.24")
        @JvmStatic
        fun setMinutes(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            val zdt = thisTimeValue(thisValue, "setMinutes") ?: ZonedDateTime.ofInstant(
                Instant.EPOCH,
                AOs.defaultZone
            )
            expect(thisValue is JSDateObject)
            val minute = arguments.argument(0).toNumber()
            val second = if (arguments.size > 1) arguments[1].toNumber() else getSeconds(arguments)
            val milli = if (arguments.size > 2) arguments[2].toNumber() else getMilliseconds(arguments)
            ifAnyNotFinite(thisValue, minute, second, milli) { return it }

            return dateValueSetHelper(
                thisValue,
                zdt.withMinute(minute.asInt).withSecond(second.asInt).withNano(milli.asInt * 1_000_000)
            )
        }

        @ECMAImpl("21.4.4.25")
        @JvmStatic
        fun setMonth(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            val zdt = thisTimeValue(thisValue, "setMonth") ?: ZonedDateTime.ofInstant(
                Instant.EPOCH,
                AOs.defaultZone
            )
            expect(thisValue is JSDateObject)
            val month = arguments.argument(0).toNumber()
            val date = if (arguments.size > 1) arguments[1].toNumber() else getDate(arguments)
            ifAnyNotFinite(thisValue, month, date) { return it }

            return dateValueSetHelper(thisValue, zdt.withMonth(month.asInt).withDayOfMonth(date.asInt))
        }

        @ECMAImpl("21.4.4.26")
        @JvmStatic
        fun setSeconds(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            val zdt = thisTimeValue(thisValue, "setSeconds") ?: ZonedDateTime.ofInstant(
                Instant.EPOCH,
                AOs.defaultZone
            )
            expect(thisValue is JSDateObject)
            val second = arguments.argument(0).toNumber()
            val milli =
                if (arguments.size > 1) arguments[1].toNumber() else getMilliseconds(arguments)
            ifAnyNotFinite(thisValue, second, milli) { return it }

            return dateValueSetHelper(
                thisValue,
                zdt.withSecond(second.asInt).withNano(milli.asInt * 1_000_000)
            )
        }

        @ECMAImpl("21.4.4.27")
        @JvmStatic
        fun setTime(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            thisTimeValue(thisValue, "setTime")
            expect(thisValue is JSDateObject)
            val time = arguments.argument(0).toNumber()
            ifAnyNotFinite(thisValue, time) { return it }

            val tv = ZonedDateTime.ofInstant(Instant.ofEpochMilli(time.asLong), AOs.defaultZone)
            return dateValueSetHelper(thisValue, tv)
        }

        @ECMAImpl("21.4.4.28")
        @JvmStatic
        fun setUTCDate(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            thisUTCTimeValue(thisValue, "setUTCDate")
            expect(thisValue is JSDateObject)
            val date = arguments.argument(0).toNumber()
            ifAnyNotFinite(thisValue, date) { return it }
            return dateValueSetHelper(
                thisValue,
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(date.asLong), ZoneOffset.UTC)
            )
        }

        @ECMAImpl("21.4.4.29")
        @JvmStatic
        fun setUTCFullYear(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            val zdt = thisUTCTimeValue(thisValue, "setUTCFullYear") ?: ZonedDateTime.ofInstant(
                Instant.EPOCH,
                ZoneOffset.UTC
            )
            expect(thisValue is JSDateObject)
            val year = arguments.argument(0).toNumber()
            val month = if (arguments.size > 1) arguments[1].toNumber() else getMonth(arguments)
            val date = if (arguments.size > 2) arguments[2].toNumber() else getDate(arguments)
            ifAnyNotFinite(thisValue, year, month, date) { return it }

            return dateValueSetHelper(
                thisValue,
                zdt.withYear(year.asInt).withMonth(month.asInt).withDayOfMonth(date.asInt)
            )
        }

        @ECMAImpl("21.4.4.30")
        @JvmStatic
        fun setUTCHours(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            val zdt = thisUTCTimeValue(thisValue, "setUTCHours") ?: ZonedDateTime.ofInstant(
                Instant.EPOCH,
                ZoneOffset.UTC
            )
            expect(thisValue is JSDateObject)
            val hour = arguments.argument(0).toNumber()
            val minute = if (arguments.size > 1) arguments[1].toNumber() else getMinutes(arguments)
            val second = if (arguments.size > 2) arguments[2].toNumber() else getSeconds(arguments)
            val milli = if (arguments.size > 3) arguments[3].toNumber() else getMilliseconds(arguments)
            ifAnyNotFinite(thisValue, hour, minute, second, milli) { return it }

            return dateValueSetHelper(
                thisValue,
                zdt.withHour(hour.asInt).withMinute(minute.asInt).withSecond(second.asInt)
                    .withNano(milli.asInt * 1_000_000),
            )
        }

        @ECMAImpl("21.4.4.31")
        @JvmStatic
        fun setUTCMilliseconds(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            val zdt = thisUTCTimeValue(thisValue, "setUTCMilliseconds") ?: ZonedDateTime.ofInstant(
                Instant.EPOCH,
                ZoneOffset.UTC
            )
            expect(thisValue is JSDateObject)
            val ms = arguments.argument(0).toNumber()
            ifAnyNotFinite(thisValue, ms) { return it }

            return dateValueSetHelper(thisValue, zdt.withNano(ms.asInt))
        }

        @ECMAImpl("21.4.4.32")
        @JvmStatic
        fun setUTCMinutes(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            val zdt = thisUTCTimeValue(thisValue, "setUTCMinutes") ?: ZonedDateTime.ofInstant(
                Instant.EPOCH,
                ZoneOffset.UTC
            )
            expect(thisValue is JSDateObject)
            val minute = arguments.argument(0).toNumber()
            val second = if (arguments.size > 1) arguments[1].toNumber() else getSeconds(arguments)
            val milli = if (arguments.size > 2) arguments[2].toNumber() else getMilliseconds(arguments)
            ifAnyNotFinite(thisValue, minute, second, milli) { return it }

            return dateValueSetHelper(
                thisValue,
                zdt.withMinute(minute.asInt).withSecond(second.asInt).withNano(milli.asInt * 1_000_000)
            )
        }

        @ECMAImpl("21.4.4.33")
        @JvmStatic
        fun setUTCMonth(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            val zdt = thisUTCTimeValue(thisValue, "setUTCMonth") ?: ZonedDateTime.ofInstant(
                Instant.EPOCH,
                ZoneOffset.UTC
            )
            expect(thisValue is JSDateObject)
            val month = arguments.argument(0).toNumber()
            val date = if (arguments.size > 1) arguments[1].toNumber() else getDate(arguments)
            ifAnyNotFinite(thisValue, month, date) { return it }

            return dateValueSetHelper(thisValue, zdt.withMonth(month.asInt).withDayOfMonth(date.asInt))
        }

        @ECMAImpl("21.4.4.34")
        @JvmStatic
        fun setUTCSeconds(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            val zdt = thisUTCTimeValue(thisValue, "setUTCSeconds") ?: ZonedDateTime.ofInstant(
                Instant.EPOCH,
                ZoneOffset.UTC
            )
            expect(thisValue is JSDateObject)
            val second = arguments.argument(0).toNumber()
            val milli = if (arguments.size > 1) arguments[1].toNumber() else getMilliseconds(arguments)
            ifAnyNotFinite(thisValue, second, milli) { return it }

            return dateValueSetHelper(
                thisValue,
                zdt.withSecond(second.asInt).withNano(milli.asInt * 1_000_000)
            )
        }

        @ECMAImpl("21.4.4.35")
        @JvmStatic
        fun toDateString(arguments: JSArguments): JSValue {
            val zdt = thisTimeValue(arguments.thisValue, "toDateString") ?: return "Invalid Date".toValue()
            return AOs.dateString(zdt).toValue()
        }

        @ECMAImpl("21.4.4.36")
        @JvmStatic
        fun toISOString(arguments: JSArguments): JSValue {
            var ztd = thisTimeValue(arguments.thisValue, "toISOString")?.toOffsetDateTime()
                ?.atZoneSameInstant(ZoneOffset.UTC)
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

        @ECMAImpl("21.4.4.37")
        @JvmStatic
        fun toJSON(arguments: JSArguments): JSValue {
            val obj = arguments.thisValue.toObject()
            val tv = obj.toPrimitive(AOs.ToPrimitiveHint.AsNumber)
            if (tv is JSNumber && !tv.isFinite)
                return JSNull
            return AOs.invoke(obj, "toISOString".toValue())
        }

        @ECMAImpl("21.4.4.41")
        @JvmStatic
        fun toString(arguments: JSArguments): JSValue {
            return AOs.toDateString(
                thisTimeValue(arguments.thisValue, "toString") ?: return "Invalid Date".toValue()
            ).toValue()
        }

        @ECMAImpl("21.4.4.42")
        @JvmStatic
        fun toTimeString(arguments: JSArguments): JSValue {
            val zdt = thisTimeValue(arguments.thisValue, "toTimeString") ?: return "Invalid Date".toValue()
            return (AOs.timeString(zdt) + AOs.timeZoneString(zdt)).toValue()
        }

        @ECMAImpl("21.4.4.43")
        @JvmStatic
        fun toUTCString(arguments: JSArguments): JSValue {
            val zdt = thisTimeValue(arguments.thisValue, "toUTCString") ?: return "Invalid Date".toValue()
            val weekday = zdt.dayOfWeek.getDisplayName(TextStyle.SHORT, AOs.defaultLocale)
            val month = zdt.month.getDisplayName(TextStyle.SHORT, AOs.defaultLocale)
            val day = "%02d".format(zdt.dayOfMonth)
            val yearSign = if (zdt.year >= 0) "" else "-"
            val paddedYear = zdt.year.toString().padStart(4, '0')

            return "$weekday, $day $month $yearSign$paddedYear ${AOs.timeString(zdt)}".toValue()
        }

        @ECMAImpl("21.4.4.44")
        @JvmStatic
        fun valueOf(arguments: JSArguments): JSValue {
            return thisTimeValue(arguments.thisValue, "valueOf")?.toInstant()?.toEpochMilli()?.toValue()
                ?: JSNumber.NaN
        }

        @ECMAImpl("21.4.4.45")
        @JvmStatic
        fun symbolToPrimitive(arguments: JSArguments): JSValue {
            if (arguments.thisValue !is JSObject)
                Errors.IncompatibleMethodCall("Date.prototype[Symbol.toPrimitive]").throwTypeError()

            val hint = arguments.argument(0).toJSString().string

            return when (hint) {
                "string", "default" -> AOs.ordinaryToPrimitive(
                    arguments.thisValue,
                    AOs.ToPrimitiveHint.AsString
                )
                "number" -> AOs.ordinaryToPrimitive(
                    arguments.thisValue,
                    AOs.ToPrimitiveHint.AsNumber
                )
                else -> Errors.InvalidToPrimitiveHint(hint).throwTypeError()
            }
        }

        private fun dateValueSetHelper(dateObj: JSObject, zdt: ZonedDateTime): JSValue {
            return if (AOs.timeClip(zdt) == null) {
                dateObj[Slot.DateValue] = null
                JSNumber.NaN
            } else {
                dateObj[Slot.DateValue] = zdt
                zdt.toInstant().toEpochMilli().toValue()
            }
        }

        private inline fun ifAnyNotFinite(dateObj: JSObject, vararg values: JSValue, returner: (JSValue) -> Unit) {
            if (values.any { !it.isFinite }) {
                dateObj[Slot.DateValue] = null
                returner(JSNumber.NaN)
            }
        }
    }
}
