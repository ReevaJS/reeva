package com.reevajs.reeva.runtime.intl

import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.utils.toValue

class JSIntlObject(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("Locale", realm.localeCtor)
        defineOwnProperty("NumberFormat", realm.numberFormatCtor)
        defineBuiltin("getCanonicalLocales", 1, ReevaBuiltin.IntlGetCanonicalLocales)
    }

    companion object {
        fun create(realm: Realm) = JSIntlObject(realm).initialize()

        @JvmStatic
        fun getCanonicalLocales(realm: Realm, arguments: JSArguments): JSValue {
            val ll = IntlAOs.canonicalizeLocaleList(realm, arguments.argument(0))
            return Operations.createArrayFromList(realm, ll.map(String::toValue))
        }
    }
}