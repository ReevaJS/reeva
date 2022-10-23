package com.reevajs.reeva.jvmcompat

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.PropertyKey
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.*

class JSPackageObject private constructor(
    realm: Realm,
    val packageName: String?,
) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init() 

        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, (packageName ?: "<root Package>").toValue(), Descriptor.CONFIGURABLE)
    }

    override fun get(property: PropertyKey, receiver: JSValue): JSValue {
        val protoProp = super.get(property, receiver)

        if (!property.isString)
            return protoProp

        // Return protoProp if it is not JSUndefined
        if (protoProp != JSUndefined)
            return protoProp

        val name = property.asString

        val attemptedClassName = if (packageName != null) "$packageName.$name" else name

        return classObjectsCache.getOrPut(attemptedClassName) {
            try {
                JSClassObject.create(Class.forName(attemptedClassName))
            } catch (e: ClassNotFoundException) {
                return create(attemptedClassName, realm)
            }
        }
    }

    override fun delete(property: PropertyKey): Boolean {
        Errors.JVMPackage.InvalidDelete.throwTypeError()
    }

    override fun set(property: PropertyKey, value: JSValue, receiver: JSValue): Boolean {
        Errors.JVMPackage.InvalidSet.throwTypeError()
    }

    override fun isExtensible() = true

    override fun preventExtensions(): Boolean {
        Errors.JVMPackage.InvalidPreventExtensions.throwTypeError()
    }

    override fun hasProperty(property: PropertyKey): Boolean {
        // TODO
        return false
    }

    override fun ownPropertyKeys(onlyEnumerable: Boolean): List<PropertyKey> {
        return emptyList()
    }

    companion object {
        private val classObjectsCache = mutableMapOf<String, JSClassObject>()

        fun create(name: String?, realm: Realm = Agent.activeAgent.getActiveRealm()) =
            JSPackageObject(realm, name).initialize()
    }
}
