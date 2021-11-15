package com.reevajs.reeva.runtime.other

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.core.lifecycle.ModuleRecord
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.PropertyKey
import com.reevajs.reeva.runtime.primitives.JSEmpty
import com.reevajs.reeva.runtime.primitives.JSNull
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue

class JSModuleNamespaceObject private constructor(
    realm: Realm,
    val moduleRecord: ModuleRecord,
    val exports: List<String>
) : JSObject(realm) {
    override fun init() {
        super.init()

        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "Module".toValue(), Descriptor.HAS_BASIC)
    }

    override fun getPrototype() = JSNull

    override fun setPrototype(newPrototype: JSValue): Boolean {
        return Operations.setImmutablePrototype(this, newPrototype)
    }

    override fun isExtensible() = false

    override fun preventExtensions() = true

    override fun getOwnPropertyDescriptor(property: PropertyKey): Descriptor? {
        if (property.isSymbol)
            return super.getOwnPropertyDescriptor(property)

        val stringProp = property.toString()
        if (stringProp !in exports)
            return null

        val value = get(property)
        return Descriptor(value, Descriptor.WRITABLE or Descriptor.ENUMERABLE or Descriptor.HAS_CONFIGURABLE)
    }

    override fun defineOwnProperty(property: PropertyKey, descriptor: Descriptor): Boolean {
        if (property.isSymbol)
            return super.defineOwnProperty(property, descriptor)

        val current = getOwnPropertyDescriptor(property) ?: return false
        if (descriptor.isConfigurable)
            return false
        if (descriptor.hasEnumerable && !descriptor.isEnumerable)
            return false
        if (descriptor.isAccessorDescriptor)
            return false
        if (descriptor.hasWritable && !descriptor.isWritable)
            return false

        val value = descriptor.getRawValue()
        if (value != JSEmpty)
            return value.sameValue(current.getRawValue())

        return true
    }

    override fun hasProperty(property: PropertyKey): Boolean {
        return if (property.isSymbol) {
            super.hasProperty(property)
        } else property.toString() in exports
    }

    override fun get(property: PropertyKey, receiver: JSValue): JSValue {
        if (property.isSymbol)
            return super.get(property, receiver)

        val stringProp = property.toString()
        if (stringProp !in exports)
            return JSUndefined

        val binding = moduleRecord.env.getBinding(stringProp)
        if (binding == JSEmpty)
            Errors.CircularImport(stringProp).throwReferenceError()
        return binding
    }

    override fun set(property: PropertyKey, value: JSValue, receiver: JSValue) = false

    override fun delete(property: PropertyKey): Boolean {
        if (property.isSymbol)
            return super.delete(property)
        return property.toString() !in exports
    }

    override fun ownPropertyKeys(onlyEnumerable: Boolean): List<PropertyKey> {
        return exports.map(PropertyKey::from) + super.ownPropertyKeys(onlyEnumerable)
    }

    companion object {
        fun create(module: ModuleRecord, exports: List<String>, realm: Realm = Agent.activeAgent.getActiveRealm()) =
            JSModuleNamespaceObject(realm, module, exports).initialize()
    }
}