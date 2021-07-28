package com.reevajs.reeva.runtime.intl

import com.ibm.icu.number.LocalizedNumberFormatter
import com.ibm.icu.text.PluralRules
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.toNumber
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue

class JSPluralRulesProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineBuiltin("select", 1, ReevaBuiltin.PluralRulesProtoSelect)
    }

    companion object {
        fun create(realm: Realm) = JSPluralRulesProto(realm).initialize()

        @JvmStatic
        fun select(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (thisValue !is JSObject || !thisValue.hasSlot(SlotName.InitializedPluralRules))
                Errors.IncompatibleMethodCall("Intl.PluralRules.prototype.select").throwTypeError(realm)

            val numberFormatter = thisValue.getSlotAs<LocalizedNumberFormatter>(SlotName.NumberFormatter)
            val pluralRules = thisValue.getSlotAs<PluralRules>(SlotName.PluralRules)

            val number = arguments.argument(0).toNumber(realm).number
            val formattedNumber = numberFormatter.format(number)
            return pluralRules.select(formattedNumber).toValue()
        }
    }
}
