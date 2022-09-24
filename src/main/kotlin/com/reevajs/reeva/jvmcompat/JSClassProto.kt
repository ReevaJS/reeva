package com.reevajs.reeva.jvmcompat

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.utils.expect
import com.reevajs.reeva.utils.toValue

class JSClassProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineBuiltin("toString", 0, ::toString)
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSClassProto(realm).initialize()

        @JvmStatic
        fun toString(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            expect(thisValue is JSClassObject)
            return "Class(${thisValue.clazz.name})".toValue()
        }
    }
}
