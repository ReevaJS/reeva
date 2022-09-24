package com.reevajs.reeva.jvmcompat

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.utils.expect
import com.reevajs.reeva.utils.toValue

class JSClassProto private constructor(realm: Realm) : JSObject(realm.objectProto) {
    override fun init(realm: Realm) {
        super.init(realm)

        defineBuiltin(realm, "toString", 0, ::toString)
    }

    companion object {
        fun create(realm: Realm) = JSClassProto(realm).initialize(realm)

        @JvmStatic
        fun toString(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            expect(thisValue is JSClassObject)
            return "Class(${thisValue.clazz.name})".toValue()
        }
    }
}
