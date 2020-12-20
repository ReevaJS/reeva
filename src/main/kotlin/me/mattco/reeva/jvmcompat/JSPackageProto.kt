package me.mattco.reeva.jvmcompat

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.toValue

class JSPackageProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()
        defineNativeFunction("toString", 0, ::toString)
    }

    private fun toString(thisValue: JSValue, arguments: JSArguments): JSValue {
        val packageName = thisPackageObject(thisValue, "toString").packageName
        return if (packageName == null) {
            "TopLevelPackage"
        } else {
            "Package($packageName)"
        }.toValue()
    }

    companion object {
        fun create(realm: Realm) = JSPackageProto(realm).initialize()

        private fun thisPackageObject(thisValue: JSValue, methodName: String): JSPackageObject {
            if (thisValue !is JSPackageObject)
                Errors.IncompatibleMethodCall("Package.prototype.$methodName").throwTypeError()
            return thisValue
        }
    }
}
