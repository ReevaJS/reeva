package com.reevajs.reeva.runtime.module

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.PropertyKey
import com.reevajs.reeva.runtime.primitives.JSEmpty
import com.reevajs.reeva.runtime.primitives.JSNull
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.expect
import com.reevajs.reeva.utils.key
import com.reevajs.reeva.utils.toValue

open class JSModuleNamespaceObject(
    realm: Realm,
    private val exports: List<String>
) : JSObject(realm, JSNull) {
    override fun init() {
        super.init()

        defineOwnProperty(Realm.`@@toStringTag`, "Module".toValue(), 0)
    }

    override fun setPrototype(newPrototype: JSValue): Boolean {
        return Operations.setImmutablePrototype(this, newPrototype)
    }

    override fun isExtensible() = false

    override fun preventExtensions() = true

    override fun getOwnPropertyDescriptor(property: PropertyKey): Descriptor? {
        if (property.isSymbol)
            return super.getOwnPropertyDescriptor(property)

        expect(property.isString)
        val key = property.asString
        if (key !in exports)
            return null

        val value = get(property)
        return Descriptor(value, Descriptor.ENUMERABLE or Descriptor.WRITABLE)
    }

    override fun defineOwnProperty(property: PropertyKey, descriptor: Descriptor): Boolean {
        if (property.isSymbol)
            return super.defineOwnProperty(property, descriptor)

        val current = getOwnPropertyDescriptor(property) ?: return false
        if (descriptor.isAccessorDescriptor)
            return false
        if (descriptor.hasWritable && !descriptor.isWritable)
            return false
        if (descriptor.hasEnumerable && !descriptor.isEnumerable)
            return false
        if (descriptor.hasConfigurable && descriptor.isConfigurable)
            return false
        if (descriptor.getRawValue() != JSEmpty)
            return descriptor.getActualValue(realm, this).sameValue(current.getActualValue(realm, this))
        return true
    }

    override fun hasProperty(property: PropertyKey): Boolean {
        if (property.isSymbol)
            return super.hasProperty(property)
        return property.asString in exports
    }

    override fun get(property: PropertyKey, receiver: JSValue): JSValue {
        if (property.isSymbol)
            return super.get(property, receiver)

        if (property.asString !in exports)
            return JSUndefined

        return JSEmpty
//        val binding = module.resolveExport(property.asString)
//        ecmaAssert(binding != null)
//        if (binding.bindingName == "*namespace*")
//            return binding.module.namespaceObject
//
//        return binding.module.resolveBinding(binding.bindingName)
    }

    override fun set(property: PropertyKey, value: JSValue, receiver: JSValue) = false

    override fun delete(property: PropertyKey): Boolean {
        if (property.isSymbol)
            return super.delete(property)
        return property.asString !in exports
    }

    override fun ownPropertyKeys(onlyEnumerable: Boolean): List<PropertyKey> {
        return exports.map(String::key) + super.ownPropertyKeys(onlyEnumerable)
    }

    companion object {
        fun create(
            realm: Realm,
            exports: List<String>
        ) = JSModuleNamespaceObject(realm, exports.sorted()).initialize()
    }
}
