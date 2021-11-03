package com.reevajs.reeva.jvmcompat

import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.PropertyKey
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors

class JSPackageObject private constructor(
    realm: Realm,
    val packageName: String?,
) : JSObject(realm, realm.packageProto) {
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
            packageName == null -> create(realm, name)
            packageObj == null -> create(realm, "$packageName.$name")
            else -> {
                val className = "$packageName.$name"
                classObjectsCache.getOrPut(className) {
                    try {
                        val clazz = Class.forName(className)
                        JSClassObject.create(realm, clazz)
                    } catch (e: ClassNotFoundException) {
                        return create(realm, "$packageName.$name")
                    }
                }
            }
        }
    }

    override fun delete(property: PropertyKey): Boolean {
        Errors.JVMPackage.InvalidDelete.throwTypeError(realm)
    }

    override fun set(property: PropertyKey, value: JSValue, receiver: JSValue): Boolean {
        Errors.JVMPackage.InvalidSet.throwTypeError(realm)
    }

    override fun isExtensible() = true

    override fun preventExtensions(): Boolean {
        Errors.JVMPackage.InvalidPreventExtensions.throwTypeError(realm)
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

        fun create(realm: Realm, name: String? = null) = JSPackageObject(realm, name).initialize()
    }
}
