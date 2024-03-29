package com.reevajs.reeva.runtime.other

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot
import java.time.ZonedDateTime

// null dateValue indicates an invalid date (usually when passing NaN into the date ctor)
class JSDateObject private constructor(realm: Realm, dateValue: ZonedDateTime?) : JSObject(realm, realm.dateProto) {
    var dateValue by slot(Slot.DateValue, dateValue)

    companion object {
        fun create(dateValue: ZonedDateTime?, realm: Realm = Agent.activeAgent.getActiveRealm()) =
            JSDateObject(realm, dateValue).initialize()
    }
}
