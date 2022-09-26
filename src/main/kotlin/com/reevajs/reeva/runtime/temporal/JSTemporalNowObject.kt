package com.reevajs.reeva.runtime.temporal

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject

class JSTemporalNowObject(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineBuiltin("timeZone", 0, ::timeZone)
        defineBuiltin("instant", 0, ::instant)
        defineBuiltin("plainDateTime", 1, ::plainDateTime)
        defineBuiltin("plainDateTimeISO", 0, ::plainDateTimeISO)
        defineBuiltin("zonedDateTime", 1, ::zonedDateTime)
        defineBuiltin("zonedDateTimeISO", 0, ::zonedDateTimeISO)
        defineBuiltin("plainDate", 1, ::plainDate)
        defineBuiltin("plainDateISO", 0, ::plainDateISO)
        defineBuiltin("plainTimeISO", 0, ::plainTimeISO)
    }

    private fun timeZone(arguments: JSArguments): JSValue {
        TODO()
    }

    private fun instant(arguments: JSArguments): JSValue {
        return TemporalAOs.systemInstant()
    }

    private fun plainDateTime(arguments: JSArguments): JSValue {
        TODO()
    }

    private fun plainDateTimeISO(arguments: JSArguments): JSValue {
        TODO()
    }

    private fun zonedDateTime(arguments: JSArguments): JSValue {
        TODO()
    }

    private fun zonedDateTimeISO(arguments: JSArguments): JSValue {
        TODO()
    }

    private fun plainDate(arguments: JSArguments): JSValue {
        TODO()
    }

    private fun plainDateISO(arguments: JSArguments): JSValue {
        TODO()
    }

    private fun plainTimeISO(arguments: JSArguments): JSValue {
        TODO()
    }

    companion object {
        fun create(realm: Realm) = JSTemporalNowObject(realm).initialize()
    }
}
