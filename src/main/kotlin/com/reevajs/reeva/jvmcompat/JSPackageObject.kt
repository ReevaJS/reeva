package com.reevajs.reeva.jvmcompat

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.PropertyKey
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors

class JSPackageObject private constructor(
    realm: Realm,
    val packageName: String?,
) : JSObject(realm, realm.objectProto) {
    private val packageObj = if (packageName == null) null else Package.getPackage(packageName)

    override fun get(property: PropertyKey, receiver: JSValue): JSValue {
        val protoProp = super.get(property, receiver)

        if (!property.isString)
            return protoProp

        // Return protoProp if it is not JSUndefined
        if (protoProp != JSUndefined)
            return protoProp

        val name = property.asString

        return when {
            packageName == null -> create(name)
            packageObj == null -> create("$packageName.$name")
            else -> {
                val className = "$packageName.$name"
                classObjectsCache.getOrPut(className) {
                    try {
                        val clazz = Class.forName(className)
                        JSClassObject.create(clazz)
                    } catch (e: ClassNotFoundException) {
                        return create("$packageName.$name")
                    }
                }
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
