package com.reevajs.reeva.runtime.intl

import com.ibm.icu.number.LocalizedNumberFormatter
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue

class JSNumberFormatProto(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("constructor", realm.numberFormatCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "Intl.NumberFormat".toValue(), Descriptor.CONFIGURABLE)

        defineBuiltinGetter("format", ReevaBuiltin.NumberFormatProtoGetFormat)
        defineBuiltin("formatToParts", 1, ReevaBuiltin.NumberFormatProtoFormatToParts)
        defineBuiltin("resolvedOptions", 0, ReevaBuiltin.NumberFormatProtoResolvedOptions)
    }

    companion object {
        fun create(realm: Realm) = JSNumberFormatProto(realm).initialize()

        @JvmStatic
        fun format(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (thisValue !is JSObject || !thisValue.hasSlot(SlotName.InitializedNumberFormat))
                Errors.IncompatibleMethodCall("get Intl.NumberFormat.prototype.format").throwTypeError(realm)

            if (!thisValue.hasSlot(SlotName.BoundFormat)) {
                val numberFormatter = thisValue.getSlotAs<LocalizedNumberFormatter>(SlotName.NumberFormatter)
                val numberFormatFunction = JSNativeFunction.fromLambda(realm, "", 0) { r, args ->
                    IntlAOs.formatNumeric(numberFormatter, args.argument(0).toNumeric(r))
                }
                val boundNumberFormatFunction = Operations.boundFunctionCreate(
                    realm,
                    numberFormatFunction,
                    JSArguments(emptyList(), thisValue),
                )

                thisValue.setSlot(SlotName.BoundFormat, boundNumberFormatFunction)
                return boundNumberFormatFunction
            }

            return thisValue.getSlotAs(SlotName.BoundFormat)
        }

        @JvmStatic
        fun formatToParts(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (thisValue !is JSObject || !thisValue.hasSlot(SlotName.InitializedNumberFormat))
                Errors.IncompatibleMethodCall("Intl.NumberFormat.prototype.formatToParts").throwTypeError(realm)

            val numberFormatter = thisValue.getSlotAs<LocalizedNumberFormatter>(SlotName.NumberFormatter)

            return IntlAOs.formatNumericToParts(realm, numberFormatter, arguments.argument(0))
        }

        @JvmStatic
        fun resolvedOptions(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (thisValue !is JSObject || !thisValue.hasSlot(SlotName.InitializedNumberFormat))
                Errors.IncompatibleMethodCall("Intl.NumberFormat.prototype.formatToParts").throwTypeError(realm)

            val options = JSObject.create(realm)

            val numberFormatter = thisValue.getSlotAs<LocalizedNumberFormatter>(SlotName.NumberFormatter)
            val skeleton = numberFormatter.toSkeleton()
            val style = getStyle(skeleton)

            options.set("locale", thisValue.getSlotAs<String>(SlotName.Locale).toValue())
            options.set("numberingSystem", getNumberingSystem(skeleton).toValue())
            options.set("style", style.name.lowercase().toValue())

            val currency = getCurrency(skeleton)
            if (currency.isNotEmpty()) {
                options.set("currency", currency.toValue())
                options.set("currencyDisplay", getCurrencyDisplay(skeleton).toValue())
                options.set("currencySign", getCurrencySign(skeleton).toValue())
            }

            if (style == IntlAOs.NumberStyle.Unit) {
                val unit = getUnit(skeleton)
                options.set("unit", unit.toValue())
                options.set("unitDisplay", getUnitDisplay(skeleton).toValue())
            }

            options.set("minimumIntegerDigits", getMinimumIntegerDigits(skeleton).toValue())

            var pair = getSignificantDigits(skeleton)
            if (pair != null) {
                options.set("minimumSignificantDigits", pair.first.toValue())
                options.set("maximumSignificantDigits", pair.second.toValue())
            } else {
                pair = getFractionDigits(skeleton) ?: (0 to 0)
                options.set("minimumFractionDigits", pair.first.toValue())
                options.set("maximumFractionDigits", pair.second.toValue())
            }

            options.set("useGrouping", getUseGrouping(skeleton).toValue())
            val notation = getNotation(skeleton)
            options.set("notation", notation.toValue())

            if (notation == "compact")
                options.set("compactDisplay", getCompactDisplay(skeleton).toValue())

            options.set("signDisplay", getSignDisplay(skeleton).toValue())

            return options
        }

        private fun getNumberingSystem(skeleton: String): String {
            var index = skeleton.indexOf("numbering-system/")
            if (index == -1)
                return "latn"
            index += "numbering-system/".length
            return skeleton.substring(index).takeWhile { it != ' ' }
        }

        private fun getStyle(skeleton: String): IntlAOs.NumberStyle {
            if ("currency/" in skeleton)
                return IntlAOs.NumberStyle.Currency
            if ("percent" in skeleton) {
                if ("scale/100" in skeleton)
                    return IntlAOs.NumberStyle.Percent
                return IntlAOs.NumberStyle.Unit
            }
            if ("unit/" in skeleton)
                return IntlAOs.NumberStyle.Unit
            return IntlAOs.NumberStyle.Decimal
        }

        private fun getCurrency(skeleton: String): String {
            var index = skeleton.indexOf("currency/")
            if (index == -1)
                return ""
            index += "currency/".length
            return skeleton.substring(index, index + 3)
        }

        private fun getCurrencyDisplay(skeleton: String): String {
            if ("unit-width-iso-code" in skeleton)
                return "code"
            if ("unit-width-full-name" in skeleton)
                return "name"
            if ("unit-width-narrow" in skeleton)
                return "narrowSymbol"
            return "symbol"
        }

        private fun getCurrencySign(skeleton: String): String {
            if ("sign-accounting" in skeleton)
                return "accounting"
            return "standard"
        }

        private fun getUnit(skeleton: String): String {
            var begin = skeleton.indexOf("unit/")
            if (begin == -1) {
                if ("percent" in skeleton)
                    return "percent"
                return ""
            }

            begin += "unit/".length
            var end = skeleton.indexOf(" ", begin)
            if (end == -1)
                end = skeleton.length
            return skeleton.substring(begin, end)
        }

        private fun getUnitDisplay(skeleton: String): String {
            if ("unit-width-full-name" in skeleton)
                return "long"
            if ("unit-width-narrow" in skeleton)
                return "narrow"
            return "short"
        }

        private fun getMinimumIntegerDigits(skeleton: String): Int {
            var index = skeleton.indexOf("integer-width/*")
            if (index == -1)
                return 1
            index += "integer-width/*".length
            var matched = 0
            while (index < skeleton.length && skeleton[index] == '0') {
                index++
                matched++
            }
            return matched
        }

        private fun getFractionDigits(skeleton: String): Pair<Int, Int>? {
            var index = skeleton.indexOf('.')
            if (index == -1)
                return null

            index++
            var minimum = 0
            while (index < skeleton.length && skeleton[index] == '0') {
                minimum++
                index++
            }

            var maximum = minimum
            while (index < skeleton.length && skeleton[index] == '#') {
                maximum++
                index++
            }

            return minimum to maximum
        }

        private fun getSignificantDigits(skeleton: String): Pair<Int, Int>? {
            var index = skeleton.indexOf('@')
            if (index == -1)
                return null

            index++
            var minimum = 0
            while (index < skeleton.length && skeleton[index] == '@') {
                minimum++
                index++
            }

            var maximum = minimum
            while (index < skeleton.length && skeleton[index] == '#') {
                maximum++
                index++
            }

            return minimum to maximum
        }

        private fun getUseGrouping(skeleton: String): Boolean {
            return "group-off" !in skeleton
        }

        private fun getNotation(skeleton: String): String {
            if ("scientific" in skeleton)
                return "scientific"
            if ("engineering" in skeleton)
                return "engineering"
            if ("compact-" in skeleton)
                return "compact"
            return "standard"
        }

        private fun getCompactDisplay(skeleton: String): String {
            if ("compact-long" in skeleton)
                return "long"
            return "short"
        }

        private fun getSignDisplay(skeleton: String): String {
            if ("sign-never" in skeleton)
                return "never"
            if ("sign-always" in skeleton || "sign-accounting-always" in skeleton)
                return "always"
            if ("sign-except-zero" in skeleton || "sign-accounting-except-zero" in skeleton)
                return "exceptZero"
            return "auto"
        }
    }
}
