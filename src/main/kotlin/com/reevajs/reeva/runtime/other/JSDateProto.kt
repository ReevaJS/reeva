package com.reevajs.reeva.runtime.other

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.builtins.Builtin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
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
        defineBuiltin("getDate", 0, Builtin.DateProtoGetDate)
        defineBuiltin("getDay", 0, Builtin.DateProtoGetDay)
        defineBuiltin("getFullYear", 0, Builtin.DateProtoGetFullYear)
        defineBuiltin("getHours", 0, Builtin.DateProtoGetHours)
        defineBuiltin("getMilliseconds", 0, Builtin.DateProtoGetMilliseconds)
        defineBuiltin("getMinutes", 0, Builtin.DateProtoGetMinutes)
        defineBuiltin("getMonth", 0, Builtin.DateProtoGetMonth)
        defineBuiltin("getSeconds", 0, Builtin.DateProtoGetSeconds)
        defineBuiltin("getTime", 0, Builtin.DateProtoGetTime)
        defineBuiltin("getTimezoneOffset", 0, Builtin.DateProtoGetTimezoneOffset)
        defineBuiltin("getUTCDate", 0, Builtin.DateProtoGetUTCDate)
        defineBuiltin("getUTCFullYear", 0, Builtin.DateProtoGetUTCFullYear)
        defineBuiltin("getUTCHours", 0, Builtin.DateProtoGetUTCHours)
        defineBuiltin("getUTCMilliseconds", 0, Builtin.DateProtoGetUTCMilliseconds)
        defineBuiltin("getUTCMinutes", 0, Builtin.DateProtoGetUTCMinutes)
        defineBuiltin("getUTCMonth", 0, Builtin.DateProtoGetUTCMonth)
        defineBuiltin("getUTCSeconds", 0, Builtin.DateProtoGetUTCSeconds)
        defineBuiltin("setDate", 1, Builtin.DateProtoSetDate)
        defineBuiltin("setFullYear", 3, Builtin.DateProtoSetFullYear)
        defineBuiltin("setHours", 4, Builtin.DateProtoSetHours)
        defineBuiltin("setMilliseconds", 1, Builtin.DateProtoSetMilliseconds)
        defineBuiltin("setMonth", 2, Builtin.DateProtoSetMonth)
        defineBuiltin("setSeconds", 2, Builtin.DateProtoSetSeconds)
        defineBuiltin("setTime", 2, Builtin.DateProtoSetTime)
        defineBuiltin("setUTCDate", 1, Builtin.DateProtoSetUTCDate)
        defineBuiltin("setUTCFullYear", 3, Builtin.DateProtoSetUTCFullYear)
        defineBuiltin("setUTCHours", 4, Builtin.DateProtoSetUTCHours)
        defineBuiltin("setUTCMilliseconds", 1, Builtin.DateProtoSetUTCMilliseconds)
        defineBuiltin("setUTCMinutes", 3, Builtin.DateProtoSetUTCMinutes)
        defineBuiltin("setUTCMonth", 2, Builtin.DateProtoSetUTCMonth)
        defineBuiltin("setUTCSeconds", 2, Builtin.DateProtoSetUTCSeconds)
        defineBuiltin("toDateString", 0, Builtin.DateProtoToDateString)
        defineBuiltin("toISOString", 0, Builtin.DateProtoToISOString)
        defineBuiltin("toJSON", 1, Builtin.DateProtoToJSON)
        defineBuiltin("toString", 0, Builtin.DateProtoToString)
        defineBuiltin("toTimeString", 0, Builtin.DateProtoToTimeString)
        defineBuiltin("toUTCString", 0, Builtin.DateProtoToUTCString)
        defineBuiltin("valueOf", 0, Builtin.DateProtoValueOf)
        defineBuiltin(Realm.`@@toPrimitive`.key(), 0, Builtin.DateProtoSymbolToPrimitive)
    }

    companion object {
        private val isoDTF = DateTimeFormatter.ofPattern("YYYY-MM-DD'T'HH:mm:ss.AAA'Z'")
        private val isoExtendedDTF = DateTimeFormatter.ofPattern("yyyyyy-MM-DD'T'HH:mm:ss.AAA'Z'")

        fun create(realm: Realm) = JSDateProto(realm).initialize()

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

        @ECMAImpl("21.4.4.2")
        @JvmStatic
        fun getDate(realm: Realm, arguments: JSArguments): JSValue {
            return thisTimeValue(realm, arguments.thisValue, "getDate")?.dayOfMonth?.toValue() ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.3")
        @JvmStatic
        fun getDay(realm: Realm, arguments: JSArguments): JSValue {
            return thisTimeValue(realm, arguments.thisValue, "getDay")?.dayOfWeek?.value?.minus(1)?.toValue()
                ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.4")
        @JvmStatic
        fun getFullYear(realm: Realm, arguments: JSArguments): JSValue {
            return thisTimeValue(realm, arguments.thisValue, "getFullYear")?.year?.toValue() ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.5")
        @JvmStatic
        fun getHours(realm: Realm, arguments: JSArguments): JSValue {
            return thisTimeValue(realm, arguments.thisValue, "getHours")?.hour?.toValue() ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.6")
        @JvmStatic
        fun getMilliseconds(realm: Realm, arguments: JSArguments): JSValue {
            return thisTimeValue(realm, arguments.thisValue, "getMilliseconds")?.nano?.div(1_000_000L)?.toValue()
                ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.7")
        @JvmStatic
        fun getMinutes(realm: Realm, arguments: JSArguments): JSValue {
            return thisTimeValue(realm, arguments.thisValue, "getMinutes")?.minute?.toValue() ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.8")
        @JvmStatic
        fun getMonth(realm: Realm, arguments: JSArguments): JSValue {
            return thisTimeValue(realm, arguments.thisValue, "getMonth")?.monthValue?.toValue() ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.9")
        @JvmStatic
        fun getSeconds(realm: Realm, arguments: JSArguments): JSValue {
            return thisTimeValue(realm, arguments.thisValue, "getSeconds")?.second?.toValue() ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.10")
        @JvmStatic
        fun getTime(realm: Realm, arguments: JSArguments): JSValue {
            return thisTimeValue(realm, arguments.thisValue, "getTime")?.toInstant()?.toEpochMilli()?.toValue()
                ?: JSNumber.NaN
        }

        @ECMAImpl("21.4.4.11")
        @JvmStatic
        fun getTimezoneOffset(realm: Realm, arguments: JSArguments): JSValue {
            return thisTimeValue(realm, arguments.thisValue, "getTimezoneOffset")?.offset?.totalSeconds?.toValue()
                ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.12")
        @JvmStatic
        fun getUTCDate(realm: Realm, arguments: JSArguments): JSValue {
            return thisUTCTimeValue(realm, arguments.thisValue, "getUTCDate")?.dayOfMonth?.toValue()
                ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.13")
        @JvmStatic
        fun getUTCDay(realm: Realm, arguments: JSArguments): JSValue {
            return thisUTCTimeValue(realm, arguments.thisValue, "getUTCDay")?.dayOfWeek?.value?.minus(1)?.toValue()
                ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.14")
        @JvmStatic
        fun getUTCFullYear(realm: Realm, arguments: JSArguments): JSValue {
            return thisUTCTimeValue(realm, arguments.thisValue, "getUTCFullYear")?.year?.toValue()
                ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.15")
        @JvmStatic
        fun getUTCHours(realm: Realm, arguments: JSArguments): JSValue {
            return thisUTCTimeValue(realm, arguments.thisValue, "getUTCHours")?.hour?.toValue() ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.16")
        @JvmStatic
        fun getUTCMilliseconds(realm: Realm, arguments: JSArguments): JSValue {
            return thisUTCTimeValue(realm, arguments.thisValue, "getUTCMilliseconds")?.nano?.div(1_000_000L)?.toValue()
                ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.17")
        @JvmStatic
        fun getUTCMinutes(realm: Realm, arguments: JSArguments): JSValue {
            return thisUTCTimeValue(realm, arguments.thisValue, "getUTCMinutes")?.minute?.toValue()
                ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.18")
        @JvmStatic
        fun getUTCMonth(realm: Realm, arguments: JSArguments): JSValue {
            return thisUTCTimeValue(realm, arguments.thisValue, "getUTCMonth")?.monthValue?.toValue()
                ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.19")
        @JvmStatic
        fun getUTCSeconds(realm: Realm, arguments: JSArguments): JSValue {
            return thisUTCTimeValue(realm, arguments.thisValue, "getUTCSeconds")?.second?.toValue()
                ?: return JSNumber.NaN
        }

        @ECMAImpl("21.4.4.20")
        @JvmStatic
        fun setDate(realm: Realm, arguments: JSArguments): JSValue {
            val zdt = thisTimeValue(realm, arguments.thisValue, "setDate") ?: return JSNumber.NaN
            val days = Operations.toNumber(realm, arguments.argument(0))
            ifAnyNotFinite(arguments.thisValue, days) { return it }
            return dateValueSetHelper(arguments.thisValue, zdt.plusDays(days.asLong))
        }

        @ECMAImpl("21.4.4.21")
        @JvmStatic
        fun setFullYear(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            val zdt = thisTimeValue(realm, thisValue, "setFullYear") ?: ZonedDateTime.ofInstant(
                Instant.EPOCH,
                Operations.defaultZone
            )
            expect(thisValue is JSDateObject)
            val year = Operations.toNumber(realm, arguments.argument(0))
            val month = if (arguments.size > 1) Operations.toNumber(realm, arguments[1]) else getMonth(realm, arguments)
            val date = if (arguments.size > 2) Operations.toNumber(realm, arguments[2]) else getDate(realm, arguments)
            ifAnyNotFinite(thisValue, year, month, date) { return it }

            return dateValueSetHelper(
                thisValue,
                zdt.withYear(year.asInt).withMonth(month.asInt).withDayOfMonth(date.asInt)
            )
        }

        @ECMAImpl("21.4.4.22")
        @JvmStatic
        fun setHours(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            val zdt = thisTimeValue(realm, thisValue, "setHours") ?: ZonedDateTime.ofInstant(
                Instant.EPOCH,
                Operations.defaultZone
            )
            expect(thisValue is JSDateObject)
            val hour = Operations.toNumber(realm, arguments.argument(0))
            val minute =
                if (arguments.size > 1) Operations.toNumber(realm, arguments[1]) else getMinutes(realm, arguments)
            val second =
                if (arguments.size > 2) Operations.toNumber(realm, arguments[2]) else getSeconds(realm, arguments)
            val milli =
                if (arguments.size > 3) Operations.toNumber(realm, arguments[3]) else getMilliseconds(realm, arguments)
            ifAnyNotFinite(thisValue, minute, second, milli) { return it }

            return dateValueSetHelper(
                thisValue,
                zdt.withHour(hour.asInt).withMinute(minute.asInt).withSecond(second.asInt)
                    .withNano(milli.asInt * 1_000_000),
            )
        }

        @ECMAImpl("21.4.4.23")
        @JvmStatic
        fun setMilliseconds(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            val zdt = thisTimeValue(realm, thisValue, "setMilliseconds") ?: ZonedDateTime.ofInstant(
                Instant.EPOCH,
                Operations.defaultZone
            )
            expect(thisValue is JSDateObject)
            val ms = Operations.toNumber(realm, arguments.argument(0))
            ifAnyNotFinite(thisValue, ms) { return it }

            return dateValueSetHelper(thisValue, zdt.withNano(ms.asInt))
        }

        @ECMAImpl("21.4.4.24")
        @JvmStatic
        fun setMinutes(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            val zdt = thisTimeValue(realm, thisValue, "setMinutes") ?: ZonedDateTime.ofInstant(
                Instant.EPOCH,
                Operations.defaultZone
            )
            expect(thisValue is JSDateObject)
            val minute = Operations.toNumber(realm, arguments.argument(0))
            val second =
                if (arguments.size > 1) Operations.toNumber(realm, arguments[1]) else getSeconds(realm, arguments)
            val milli =
                if (arguments.size > 2) Operations.toNumber(realm, arguments[2]) else getMilliseconds(realm, arguments)
            ifAnyNotFinite(thisValue, minute, second, milli) { return it }

            return dateValueSetHelper(
                thisValue,
                zdt.withMinute(minute.asInt).withSecond(second.asInt).withNano(milli.asInt * 1_000_000)
            )
        }

        @ECMAImpl("21.4.4.25")
        @JvmStatic
        fun setMonth(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            val zdt = thisTimeValue(realm, thisValue, "setMonth") ?: ZonedDateTime.ofInstant(
                Instant.EPOCH,
                Operations.defaultZone
            )
            expect(thisValue is JSDateObject)
            val month = Operations.toNumber(realm, arguments.argument(0))
            val date = if (arguments.size > 1) Operations.toNumber(realm, arguments[1]) else getDate(realm, arguments)
            ifAnyNotFinite(thisValue, month, date) { return it }

            return dateValueSetHelper(thisValue, zdt.withMonth(month.asInt).withDayOfMonth(date.asInt))
        }

        @ECMAImpl("21.4.4.26")
        @JvmStatic
        fun setSeconds(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            val zdt = thisTimeValue(realm, thisValue, "setSeconds") ?: ZonedDateTime.ofInstant(
                Instant.EPOCH,
                Operations.defaultZone
            )
            expect(thisValue is JSDateObject)
            val second = Operations.toNumber(realm, arguments.argument(0))
            val milli =
                if (arguments.size > 1) Operations.toNumber(realm, arguments[1]) else getMilliseconds(realm, arguments)
            ifAnyNotFinite(thisValue, second, milli) { return it }

            return dateValueSetHelper(
                thisValue,
                zdt.withSecond(second.asInt).withNano(milli.asInt * 1_000_000)
            )
        }

        @ECMAImpl("21.4.4.27")
        @JvmStatic
        fun setTime(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            thisTimeValue(realm, thisValue, "setTime")
            expect(thisValue is JSDateObject)
            val time = Operations.toNumber(realm, arguments.argument(0))
            ifAnyNotFinite(thisValue, time) { return it }

            val tv = ZonedDateTime.ofInstant(Instant.ofEpochMilli(time.asLong), Operations.defaultZone)
            return dateValueSetHelper(thisValue, tv)
        }

        @ECMAImpl("21.4.4.28")
        @JvmStatic
        fun setUTCDate(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            thisUTCTimeValue(realm, thisValue, "setUTCDate")
            expect(thisValue is JSDateObject)
            val date = Operations.toNumber(realm, arguments.argument(0))
            ifAnyNotFinite(thisValue, date) { return it }
            return dateValueSetHelper(
                thisValue,
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(date.asLong), ZoneOffset.UTC)
            )
        }

        @ECMAImpl("21.4.4.29")
        @JvmStatic
        fun setUTCFullYear(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            val zdt = thisUTCTimeValue(realm, thisValue, "setUTCFullYear") ?: ZonedDateTime.ofInstant(
                Instant.EPOCH,
                ZoneOffset.UTC
            )
            expect(thisValue is JSDateObject)
            val year = Operations.toNumber(realm, arguments.argument(0))
            val month = if (arguments.size > 1) Operations.toNumber(realm, arguments[1]) else getMonth(realm, arguments)
            val date = if (arguments.size > 2) Operations.toNumber(realm, arguments[2]) else getDate(realm, arguments)
            ifAnyNotFinite(thisValue, year, month, date) { return it }

            return dateValueSetHelper(
                thisValue,
                zdt.withYear(year.asInt).withMonth(month.asInt).withDayOfMonth(date.asInt)
            )
        }

        @ECMAImpl("21.4.4.30")
        @JvmStatic
        fun setUTCHours(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            val zdt = thisUTCTimeValue(realm, thisValue, "setUTCHours") ?: ZonedDateTime.ofInstant(
                Instant.EPOCH,
                ZoneOffset.UTC
            )
            expect(thisValue is JSDateObject)
            val hour = Operations.toNumber(realm, arguments.argument(0))
            val minute =
                if (arguments.size > 1) Operations.toNumber(realm, arguments[1]) else getMinutes(realm, arguments)
            val second =
                if (arguments.size > 2) Operations.toNumber(realm, arguments[2]) else getSeconds(realm, arguments)
            val milli =
                if (arguments.size > 3) Operations.toNumber(realm, arguments[3]) else getMilliseconds(realm, arguments)
            ifAnyNotFinite(thisValue, hour, minute, second, milli) { return it }

            return dateValueSetHelper(
                thisValue,
                zdt.withHour(hour.asInt).withMinute(minute.asInt).withSecond(second.asInt)
                    .withNano(milli.asInt * 1_000_000),
            )
        }

        @ECMAImpl("21.4.4.31")
        @JvmStatic
        fun setUTCMilliseconds(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            val zdt = thisUTCTimeValue(realm, thisValue, "setUTCMilliseconds") ?: ZonedDateTime.ofInstant(
                Instant.EPOCH,
                ZoneOffset.UTC
            )
            expect(thisValue is JSDateObject)
            val ms = Operations.toNumber(realm, arguments.argument(0))
            ifAnyNotFinite(thisValue, ms) { return it }

            return dateValueSetHelper(thisValue, zdt.withNano(ms.asInt))
        }

        @ECMAImpl("21.4.4.32")
        @JvmStatic
        fun setUTCMinutes(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            val zdt = thisUTCTimeValue(realm, thisValue, "setUTCMinutes") ?: ZonedDateTime.ofInstant(
                Instant.EPOCH,
                ZoneOffset.UTC
            )
            expect(thisValue is JSDateObject)
            val minute = Operations.toNumber(realm, arguments.argument(0))
            val second =
                if (arguments.size > 1) Operations.toNumber(realm, arguments[1]) else getSeconds(realm, arguments)
            val milli =
                if (arguments.size > 2) Operations.toNumber(realm, arguments[2]) else getMilliseconds(realm, arguments)
            ifAnyNotFinite(thisValue, minute, second, milli) { return it }

            return dateValueSetHelper(
                thisValue,
                zdt.withMinute(minute.asInt).withSecond(second.asInt).withNano(milli.asInt * 1_000_000)
            )
        }

        @ECMAImpl("21.4.4.33")
        @JvmStatic
        fun setUTCMonth(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            val zdt = thisUTCTimeValue(realm, thisValue, "setUTCMonth") ?: ZonedDateTime.ofInstant(
                Instant.EPOCH,
                ZoneOffset.UTC
            )
            expect(thisValue is JSDateObject)
            val month = Operations.toNumber(realm, arguments.argument(0))
            val date = if (arguments.size > 1) Operations.toNumber(realm, arguments[1]) else getDate(realm, arguments)
            ifAnyNotFinite(thisValue, month, date) { return it }

            return dateValueSetHelper(thisValue, zdt.withMonth(month.asInt).withDayOfMonth(date.asInt))
        }

        @ECMAImpl("21.4.4.34")
        @JvmStatic
        fun setUTCSeconds(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            val zdt = thisUTCTimeValue(realm, thisValue, "setUTCSeconds") ?: ZonedDateTime.ofInstant(
                Instant.EPOCH,
                ZoneOffset.UTC
            )
            expect(thisValue is JSDateObject)
            val second = Operations.toNumber(realm, arguments.argument(0))
            val milli =
                if (arguments.size > 1) Operations.toNumber(realm, arguments[1]) else getMilliseconds(realm, arguments)
            ifAnyNotFinite(thisValue, second, milli) { return it }

            return dateValueSetHelper(
                thisValue,
                zdt.withSecond(second.asInt).withNano(milli.asInt * 1_000_000)
            )
        }

        @ECMAImpl("21.4.4.35")
        @JvmStatic
        fun toDateString(realm: Realm, arguments: JSArguments): JSValue {
            val zdt = thisTimeValue(realm, arguments.thisValue, "toDateString") ?: return "Invalid Date".toValue()
            return Operations.dateString(zdt).toValue()
        }

        @ECMAImpl("21.4.4.36")
        @JvmStatic
        fun toISOString(realm: Realm, arguments: JSArguments): JSValue {
            var ztd = thisTimeValue(realm, arguments.thisValue, "toISOString")?.toOffsetDateTime()
                ?.atZoneSameInstant(ZoneOffset.UTC)
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

        @ECMAImpl("21.4.4.37")
        @JvmStatic
        fun toJSON(realm: Realm, arguments: JSArguments): JSValue {
            val obj = Operations.toObject(realm, arguments.thisValue)
            val tv = Operations.toPrimitive(realm, obj, Operations.ToPrimitiveHint.AsNumber)
            if (tv is JSNumber && !tv.isFinite)
                return JSNull
            return Operations.invoke(realm, obj, "toISOString".toValue())
        }

        @ECMAImpl("21.4.4.41")
        @JvmStatic
        fun toString(realm: Realm, arguments: JSArguments): JSValue {
            return Operations.toDateString(
                thisTimeValue(realm, arguments.thisValue, "toString") ?: return "Invalid Date".toValue()
            ).toValue()
        }

        @ECMAImpl("21.4.4.42")
        @JvmStatic
        fun toTimeString(realm: Realm, arguments: JSArguments): JSValue {
            val zdt = thisTimeValue(realm, arguments.thisValue, "toTimeString") ?: return "Invalid Date".toValue()
            return (Operations.timeString(zdt) + Operations.timeZoneString(zdt)).toValue()
        }

        @ECMAImpl("21.4.4.43")
        @JvmStatic
        fun toUTCString(realm: Realm, arguments: JSArguments): JSValue {
            val zdt = thisTimeValue(realm, arguments.thisValue, "toUTCString") ?: return "Invalid Date".toValue()
            val weekday = zdt.dayOfWeek.getDisplayName(TextStyle.SHORT, Operations.defaultLocale)
            val month = zdt.month.getDisplayName(TextStyle.SHORT, Operations.defaultLocale)
            val day = "%02d".format(zdt.dayOfMonth)
            val yearSign = if (zdt.year >= 0) "" else "-"
            val paddedYear = zdt.year.toString().padStart(4, '0')

            return "$weekday, $day $month $yearSign$paddedYear ${Operations.timeString(zdt)}".toValue()
        }

        @ECMAImpl("21.4.4.44")
        @JvmStatic
        fun valueOf(realm: Realm, arguments: JSArguments): JSValue {
            return thisTimeValue(realm, arguments.thisValue, "valueOf")?.toInstant()?.toEpochMilli()?.toValue()
                ?: JSNumber.NaN
        }

        @ECMAImpl("21.4.4.45")
        @JvmStatic
        fun `@@toPrimitive`(realm: Realm, arguments: JSArguments): JSValue {
            if (arguments.thisValue !is JSObject)
                Errors.IncompatibleMethodCall("Date.prototype[Symbol.toPrimitive]").throwTypeError(realm)

            val hint = Operations.toString(realm, arguments.argument(0)).string

            return when (hint) {
                "string", "default" -> Operations.ordinaryToPrimitive(
                    realm,
                    arguments.thisValue,
                    Operations.ToPrimitiveHint.AsString
                )
                "number" -> Operations.ordinaryToPrimitive(
                    realm,
                    arguments.thisValue,
                    Operations.ToPrimitiveHint.AsNumber
                )
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
    }
}
