package com.reevajs.reeva.runtime.intl

import com.ibm.icu.util.ULocale
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSFalse
import com.reevajs.reeva.runtime.primitives.JSTrue
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue

class JSLocaleProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("constructor", realm.localeCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "Intl.Locale".toValue(), Descriptor.CONFIGURABLE)

        defineBuiltinGetter("baseName", ReevaBuiltin.LocaleProtoGetBaseName)
        defineBuiltinGetter("calendar", ReevaBuiltin.LocaleProtoGetCalendar)
        defineBuiltinGetter("caseFirst", ReevaBuiltin.LocaleProtoGetCaseFirst)
        defineBuiltinGetter("collation", ReevaBuiltin.LocaleProtoGetCollation)
        defineBuiltinGetter("hourCycle", ReevaBuiltin.LocaleProtoGetHourCycle)
        defineBuiltinGetter("numeric", ReevaBuiltin.LocaleProtoGetNumeric)
        defineBuiltinGetter("numberingSystem", ReevaBuiltin.LocaleProtoGetNumberingSystem)
        defineBuiltinGetter("language", ReevaBuiltin.LocaleProtoGetLanguage)
        defineBuiltinGetter("script", ReevaBuiltin.LocaleProtoGetScript)
        defineBuiltinGetter("region", ReevaBuiltin.LocaleProtoGetRegion)

        defineBuiltin("maximize", 0, ReevaBuiltin.LocaleProtoMaximize)
        defineBuiltin("minimize", 0, ReevaBuiltin.LocaleProtoMinimize)
        defineBuiltin("toString", 0, ReevaBuiltin.LocaleProtoToString)
    }

    companion object {
        fun create(realm: Realm) = JSLocaleProto(realm).initialize()

        private fun thisULocale(realm: Realm, thisValue: JSValue, methodName: String): ULocale {
            if (thisValue !is JSObject || !thisValue.hasSlot(SlotName.InitializedLocale))
                Errors.IncompatibleMethodCall("Locale.prototype.$methodName").throwTypeError(realm)
            return thisValue.getSlotAs(SlotName.ULocale)
        }

        private fun make(realm: Realm, locale: ULocale): JSObject {
            val obj = Operations.ordinaryCreateFromConstructor(
                realm,
                realm.localeCtor,
                realm.localeProto,
                listOf(SlotName.InitializedLocale, SlotName.ULocale),
            )
            obj.setSlot(SlotName.ULocale, locale)
            return obj
        }

        @JvmStatic
        fun getBaseName(realm: Realm, arguments: JSArguments): JSValue {
            val ulocale = thisULocale(realm, arguments.thisValue, "get baseName")
            return ULocale.createCanonical(ulocale.baseName).toLanguageTag().toValue()
        }

        @JvmStatic
        fun getCalendar(realm: Realm, arguments: JSArguments): JSValue {
            return unicodeKeywordValue(thisULocale(realm, arguments.thisValue, "get calendar"), "calendar")
        }

        @JvmStatic
        fun getCaseFirst(realm: Realm, arguments: JSArguments): JSValue {
            return unicodeKeywordValue(thisULocale(realm, arguments.thisValue, "get caseFirst"), "colcasefirst")
        }

        @JvmStatic
        fun getCollation(realm: Realm, arguments: JSArguments): JSValue {
            return unicodeKeywordValue(thisULocale(realm, arguments.thisValue, "get collation"), "collation")
        }

        @JvmStatic
        fun getHourCycle(realm: Realm, arguments: JSArguments): JSValue {
            return unicodeKeywordValue(thisULocale(realm, arguments.thisValue, "get hourCycle"), "hours")
        }

        @JvmStatic
        fun getNumeric(realm: Realm, arguments: JSArguments): JSValue {
            val result = thisULocale(realm, arguments.thisValue, "get numeric").getKeywordValue("colnumeric")
            return if (result == "true") JSTrue else JSFalse
        }

        @JvmStatic
        fun getNumberingSystem(realm: Realm, arguments: JSArguments): JSValue {
            return unicodeKeywordValue(thisULocale(realm, arguments.thisValue, "get numberingSystem"), "numbers")
        }

        @JvmStatic
        fun getLanguage(realm: Realm, arguments: JSArguments): JSValue {
            return thisULocale(realm, arguments.thisValue, "get baseName").language.toValue()
        }

        @JvmStatic
        fun getScript(realm: Realm, arguments: JSArguments): JSValue {
            return thisULocale(realm, arguments.thisValue, "get baseName").script.toValue()
        }

        @JvmStatic
        fun getRegion(realm: Realm, arguments: JSArguments): JSValue {
            return thisULocale(realm, arguments.thisValue, "get baseName").country.toValue()
        }

        @JvmStatic
        fun maximize(realm: Realm, arguments: JSArguments): JSValue {
            val source = thisULocale(realm, arguments.thisValue, "maximize")
            return minMaxHelper(realm, source, ULocale::addLikelySubtags)
        }

        @JvmStatic
        fun minimize(realm: Realm, arguments: JSArguments): JSValue {
            val source = thisULocale(realm, arguments.thisValue, "minimize")
            return minMaxHelper(realm, source, ULocale::minimizeSubtags)
        }

        @JvmStatic
        fun toString(realm: Realm, arguments: JSArguments): JSValue {
            return thisULocale(realm, arguments.thisValue, "toString").toLanguageTag().toValue()
        }

        private fun minMaxHelper(realm: Realm, source: ULocale, func: (ULocale) -> ULocale): JSValue {
            var result = func(ULocale(source.baseName))

            if (source.baseName.length != result.baseName.length) {
                if (source.baseName.length != source.name.length) {
                    result = ULocale.Builder()
                        .setLocale(source)
                        .setLanguage(result.language)
                        .setRegion(result.country)
                        .setScript(result.script)
                        .setVariant(result.variant)
                        .build()
                }
            } else {
                result = source
            }

            return make(realm, result)
        }

        private fun unicodeKeywordValue(locale: ULocale, key: String): JSValue {
            val value = locale.getKeywordValue(key)?.let {
                if (it == "yes") "true" else it
            } ?: return JSUndefined

            if (value == "true" && key == "kf")
                return "".toValue()

            return value.toValue()
        }
    }
}
