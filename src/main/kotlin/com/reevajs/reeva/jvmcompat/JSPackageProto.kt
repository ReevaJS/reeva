package com.reevajs.reeva.jvmcompat

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue

class JSPackageProto private constructor(realm: Realm) : JSObject(realm.objectProto) {
    override fun init(realm: Realm) {
        super.init(realm)

        defineBuiltin(realm, "toString", 0, ::toString)
    }

    companion object {
        fun create(realm: Realm) = JSPackageProto(realm).initialize(realm)

        private fun thisPackageObject(thisValue: JSValue, methodName: String): JSPackageObject {
            if (thisValue !is JSPackageObject)
                Errors.IncompatibleMethodCall("Package.prototype.$methodName").throwTypeError()
            return thisValue
        }

        @JvmStatic
        fun toString(arguments: JSArguments): JSValue {
            val packageName = thisPackageObject(arguments.thisValue, "toString").packageName
            return if (packageName == null) {
                "TopLevelPackage"
            } else {
                "Package($packageName)"
            }.toValue()
        }
    }
}
