package com.reevajs.reeva.runtime.memory

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.PropertyKey
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSEmpty
import com.reevajs.reeva.runtime.primitives.JSUndefined

class JSIntegerIndexedObject private constructor(
    realm: Realm,
    private val kind: AOs.TypedArrayKind,
    proto: JSValue = kind.getProto(realm),
) : JSObject(realm, proto) {
    // ContentType slot is just kind.isBigInt, so we don't store that

    val typedArrayKind by slot(SlotName.TypedArrayKind, kind)
    val typedArrayName by slot(SlotName.TypedArrayName, "${kind.name}Array")
    val viewedArrayBuffer by lateinitSlot(SlotName.ViewedArrayBuffer)
    val byteLength by lateinitSlot(SlotName.ByteLength)
    val byteOffset by lateinitSlot(SlotName.ByteOffset)
    val arrayLength by lateinitSlot(SlotName.ArrayLength)

    override fun getOwnPropertyDescriptor(property: PropertyKey): Descriptor? {
        if (property.isSymbol)
            return super.getOwnPropertyDescriptor(property)

        val numericIndex = AOs.canonicalNumericIndexString(property.asValue)
            ?: return super.getOwnPropertyDescriptor(property)

        val value = AOs.integerIndexedElementGet(this, numericIndex)
        if (value == JSUndefined)
            return null

        return Descriptor(value, Descriptor.DEFAULT_ATTRIBUTES)
    }

    override fun hasProperty(property: PropertyKey): Boolean {
        if (property.isSymbol)
            return super.hasProperty(property)
        val numericIndex = AOs.canonicalNumericIndexString(property.asValue)
            ?: return super.hasProperty(property)
        return AOs.isValidIntegerIndex(this, numericIndex)
    }

    override fun defineOwnProperty(property: PropertyKey, descriptor: Descriptor): Boolean {
        if (property.isSymbol)
            return super.defineOwnProperty(property, descriptor)

        val numericIndex = AOs.canonicalNumericIndexString(property.asValue)
            ?: return super.defineOwnProperty(property, descriptor)

        if (!AOs.isValidIntegerIndex(this, numericIndex))
            return false

        if (descriptor.isAccessorDescriptor)
            return false
        if (descriptor.hasConfigurable && !descriptor.isConfigurable)
            return false
        if (descriptor.hasEnumerable && !descriptor.isEnumerable)
            return false
        if (descriptor.hasWritable && !descriptor.isWritable)
            return false
        if (descriptor.getRawValue() != JSEmpty)
            AOs.integerIndexedElementSet(this, numericIndex, descriptor.getActualValue(this))
        return true
    }

    override fun get(property: PropertyKey, receiver: JSValue): JSValue {
        if (property.isSymbol)
            return super.get(property, receiver)

        val numericIndex = AOs.canonicalNumericIndexString(property.asValue)
            ?: return super.get(property, receiver)

        return AOs.integerIndexedElementGet(receiver, numericIndex)
    }

    override fun set(property: PropertyKey, value: JSValue, receiver: JSValue): Boolean {
        if (property.isSymbol)
            return super.set(property, value, receiver)

        val numericIndex = AOs.canonicalNumericIndexString(property.asValue)
            ?: return super.set(property, value, receiver)

        AOs.integerIndexedElementSet(receiver, numericIndex, value)
        return true
    }

    override fun delete(property: PropertyKey): Boolean {
        if (property.isSymbol)
            return super.delete(property)

        val numericIndex = AOs.canonicalNumericIndexString(property.asValue)
            ?: return super.delete(property)

        return !AOs.isValidIntegerIndex(this, numericIndex)
    }

    override fun ownPropertyKeys(onlyEnumerable: Boolean): List<PropertyKey> {
        val properties = if (!AOs.isDetachedBuffer(viewedArrayBuffer)) {
            (0..arrayLength).map(PropertyKey::from).toMutableList()
        } else mutableListOf()

        return properties + storage.filterValues {
            if (onlyEnumerable) (it.attributes and Descriptor.ENUMERABLE) != 0 else true
        }.map { PropertyKey.from(it.key) }
    }

    companion object {
        fun create(
            kind: AOs.TypedArrayKind,
            realm: Realm = Agent.activeAgent.getActiveRealm(),
            proto: JSValue = kind.getProto(realm),
        ) = JSIntegerIndexedObject(realm, kind, proto).initialize()
    }
}
