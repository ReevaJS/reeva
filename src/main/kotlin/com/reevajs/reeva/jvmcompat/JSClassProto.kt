package com.reevajs.reeva.jvmcompat

import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.utils.expect
import com.reevajs.reeva.utils.toValue

class JSClassProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineBuiltin("toString", 0, ReevaBuiltin.ClassProtoToString)
    }

    companion object {
        fun create(realm: Realm) = JSClassProto(realm).initialize()

        @JvmStatic
        fun toString(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            expect(thisValue is JSClassObject)
            return "Class(${thisValue.clazz.name})".toValue()
        }
    }
}
