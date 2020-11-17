package me.mattco.reeva.jvmcompat

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.PropertyKey
import me.mattco.reeva.utils.Errors

class JSPackageObject private constructor(
    realm: Realm,
    val packageName: String?,
) : JSObject(realm, realm.packageProto) {
    private val packageObj = if (packageName == null) null else Package.getPackage(packageName)

    override fun get(property: PropertyKey, receiver: JSValue): JSValue {
        val superProperty = super.get(property, receiver)
        if (!superProperty.isNullish)
            return superProperty

        val name = validatePropertyKey(property)

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

    private fun validatePropertyKey(key: PropertyKey): String {
        if (key.isSymbol)
            Errors.JVMPackage.InvalidSymbolAccess.throwTypeError()
        if (!key.isString)
            Errors.JVMPackage.InvalidNumberAccess.throwTypeError()
        return key.asString
    }

    companion object {
        private val classObjectsCache = mutableMapOf<String, JSClassObject>()

        fun create(realm: Realm, name: String? = null) = JSPackageObject(realm, name).initialize()
    }
}
