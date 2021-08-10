package com.reevajs.reeva.jvmcompat

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue

class JSPackageProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineBuiltin("toString", 0, ReevaBuiltin.PackageProtoToString)
    }

    companion object {
        fun create(realm: Realm) = JSPackageProto(realm).initialize()

        private fun thisPackageObject(realm: Realm, thisValue: JSValue, methodName: String): JSPackageObject {
            if (thisValue !is JSPackageObject)
                Errors.IncompatibleMethodCall("Package.prototype.$methodName").throwTypeError(realm)
            return thisValue
        }

        @JvmStatic
        fun toString(realm: Realm, arguments: JSArguments): JSValue {
            val packageName = thisPackageObject(realm, arguments.thisValue, "toString").packageName
            return if (packageName == null) {
                "TopLevelPackage"
            } else {
                "Package($packageName)"
            }.toValue()
        }
    }
}
