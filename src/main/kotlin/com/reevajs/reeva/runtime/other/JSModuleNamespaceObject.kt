package com.reevajs.reeva.runtime.other

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.lifecycle.ModuleRecord
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.PropertyKey
import com.reevajs.reeva.runtime.primitives.JSEmpty
import com.reevajs.reeva.runtime.primitives.JSNull
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.ecmaAssert
import com.reevajs.reeva.utils.toValue

@ECMAImpl("10.4.6")
class JSModuleNamespaceObject private constructor(
    val moduleRecord: ModuleRecord,
    val exports: List<String>
) : JSObject() {
    override fun init(realm: Realm) {
        super.init(realm)

        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "Module".toValue(), Descriptor.HAS_BASIC)
    }

    @ECMAImpl("10.4.6.1")
    override fun getPrototype() = JSNull

    @ECMAImpl("10.4.6.2")
    override fun setPrototype(newPrototype: JSValue): Boolean {
        // 1. Return ! SetImmutablePrototype(O, V)
        return Operations.setImmutablePrototype(this, newPrototype)
    }

    @ECMAImpl("10.4.6.3")
    override fun isExtensible() = false

    @ECMAImpl("10.4.6.4")
    override fun preventExtensions() = true

    @ECMAImpl("10.4.6.5")
    override fun getOwnPropertyDescriptor(property: PropertyKey): Descriptor? {
        // 1. If Type(P) is Symbol, return OrdinaryGetOwnProperty(O, P).
        if (property.isSymbol)
            return super.getOwnPropertyDescriptor(property)

        // 2. Let exports be O.[[Exports]].
        // 3. If P is not an element of exports, return undefined.
        val stringProp = property.toString()
        if (stringProp !in exports)
            return null

        // 4. Let value be ? O.[[Get]](P, O).
        val value = get(property)

        // 5. Return PropertyDescriptor { [[Value]]: value, [[Writable]]: true, [[Enumerable]]: true, [[Configurable]]: false }.
        return Descriptor(value, Descriptor.WRITABLE or Descriptor.ENUMERABLE or Descriptor.HAS_CONFIGURABLE)
    }

    @ECMAImpl("10.4.6.6")
    override fun defineOwnProperty(property: PropertyKey, descriptor: Descriptor): Boolean {
        // 1. If Type(P) is Symbol, return ! OrdinaryDefineOwnProperty(O, P, Desc).
        if (property.isSymbol)
            return super.defineOwnProperty(property, descriptor)

        // 2. Let current be ? O.[[GetOwnProperty]](P).
        // 3. If current is undefined, return false.
        val current = getOwnPropertyDescriptor(property) ?: return false

        // 4. If Desc has a [[Configurable]] field and Desc.[[Configurable]] is true, return false.
        if (descriptor.isConfigurable)
            return false

        // 5. If Desc has an [[Enumerable]] field and Desc.[[Enumerable]] is false, return false.
        if (descriptor.hasEnumerable && !descriptor.isEnumerable)
            return false

        // 6. If IsAccessorDescriptor(Desc) is true, return false.
        if (descriptor.isAccessorDescriptor)
            return false

        // 7. If Desc has a [[Writable]] field and Desc.[[Writable]] is false, return false.
        if (descriptor.hasWritable && !descriptor.isWritable)
            return false

        // 8. If Desc has a [[Value]] field, return SameValue(Desc.[[Value]], current.[[Value]]).
        val value = descriptor.getRawValue()
        if (value != JSEmpty)
            return value.sameValue(current.getRawValue())

        // 9. Return true.
        return true
    }

    @ECMAImpl("10.4.6.7")
    override fun hasProperty(property: PropertyKey): Boolean {
        // 1. If Type(P) is Symbol, return ! OrdinaryHasProperty(O, P).
        if (property.isSymbol)
            return super.hasProperty(property)

        // 2. Let exports be O.[[Exports]].
        // 3. If P is an element of exports, return true.
        // 4. Return false.
        return property.toString() in exports
    }

    @ECMAImpl("10.4.6.8")
    override fun get(property: PropertyKey, receiver: JSValue): JSValue {
        // 1. If Type(P) is Symbol, then
        if (property.isSymbol) {
            // a. Return ! OrdinaryGet(O, P, Receiver).
            return super.get(property, receiver)
        }

        // 2. Let exports be O.[[Exports]].
        val stringProp = property.toString()

        // 3. If P is not an element of exports, return undefined.
        if (stringProp !in exports)
            return JSUndefined

        // 4. Let m be O.[[Module]].
        // 5. Let binding be ! m.ResolveExport(P).
        val binding = moduleRecord.resolveExport(property.toString())

        // 6. Assert: binding is a ResolvedBinding Record.
        ecmaAssert(binding is ModuleRecord.ResolvedBinding.Record)

        // 7. Let targetModule be binding.[[Module]].
        val targetModule = binding.module

        // 8. Assert: targetModule is not undefined.
        // 9. If binding.[[BindingName]] is namespace, then
        if (binding.bindingName == null) {
            // a. Return ? GetModuleNamespace(targetModule).
            return targetModule.getModuleNamespace()
        }

        // 10. Let targetEnv be targetModule.[[Environment]].
        // 11. If targetEnv is empty, throw a ReferenceError exception.
        val targetEnv = targetModule.environment ?: Errors.CircularImport(stringProp).throwReferenceError()

        // 12. Return ? targetEnv.GetBindingValue(binding.[[BindingName]], true).
        return targetEnv.getBindingValue(binding.bindingName, isStrict = true)
    }

    @ECMAImpl("10.4.6.9")
    override fun set(property: PropertyKey, value: JSValue, receiver: JSValue) = false

    @ECMAImpl("10.4.6.10")
    override fun delete(property: PropertyKey): Boolean {
        // 1. If Type(P) is Symbol, then
        if (property.isSymbol) {
            // a. Return ! OrdinaryDelete(O, P).
            return super.delete(property)
        }

        // 2. Let exports be O.[[Exports]].
        // 3. If P is an element of exports, return false.
        // 4. Return true.
        return property.toString() !in exports
    }

    @ECMAImpl("10.4.6.11")
    override fun ownPropertyKeys(onlyEnumerable: Boolean): List<PropertyKey> {
        // 1. Let exports be O.[[Exports]].
        // 2. Let symbolKeys be OrdinaryOwnPropertyKeys(O).
        // 3. Return the list-concatenation of exports and symbolKeys.
        return exports.map(PropertyKey::from) + super.ownPropertyKeys(onlyEnumerable)
    }

    companion object {
        @ECMAImpl("10.4.6.12")
        fun create(realm: Realm, module: ModuleRecord, exports: List<String>) =
            JSModuleNamespaceObject(module, exports.sorted()).initialize(realm)
    }
}
