package me.mattco.reeva.runtime.memory

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.SlotName
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.PropertyKey
import me.mattco.reeva.runtime.primitives.JSEmpty
import me.mattco.reeva.runtime.primitives.JSUndefined

class JSIntegerIndexedObject private constructor(
    realm: Realm,
    private val kind: Operations.TypedArrayKind,
    proto: JSValue = kind.getProto(realm)
) : JSObject(realm, proto) {
    // ContentType slot is just kind.isBigInt, so we don't store that

    val typedArrayKind by slot(SlotName.TypedArrayKind, kind)
    val typedArrayName by slot(SlotName.TypedArrayName, "${kind.name}Array")
    val viewedArrayBuffer by lateinitSlot<JSObject>(SlotName.ViewedArrayBuffer)
    val byteLength by lateinitSlot<Int>(SlotName.ByteLength)
    val byteOffset by lateinitSlot<Int>(SlotName.ByteOffset)
    val arrayLength by lateinitSlot<Int>(SlotName.ArrayLength)

    override fun getOwnPropertyDescriptor(property: PropertyKey): Descriptor? {
        if (property.isSymbol)
            return super.getOwnPropertyDescriptor(property)

        val numericIndex = Operations.canonicalNumericIndexString(property.asValue)
            ?: return super.getOwnPropertyDescriptor(property)

        val value = Operations.integerIndexedElementGet(this, numericIndex)
        if (value == JSUndefined)
            return null

        return Descriptor(value, Descriptor.DEFAULT_ATTRIBUTES)
    }

    override fun hasProperty(property: PropertyKey): Boolean {
        if (property.isSymbol)
            return super.hasProperty(property)
        val numericIndex = Operations.canonicalNumericIndexString(property.asValue)
            ?: return super.hasProperty(property)
        return Operations.isValidIntegerIndex(this, numericIndex)
    }

    override fun defineOwnProperty(property: PropertyKey, descriptor: Descriptor): Boolean {
        if (property.isSymbol)
            return super.defineOwnProperty(property, descriptor)

        val numericIndex = Operations.canonicalNumericIndexString(property.asValue)
            ?: return super.defineOwnProperty(property, descriptor)

        if (!Operations.isValidIntegerIndex(this, numericIndex))
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
            Operations.integerIndexedElementSet(this, numericIndex, descriptor.getActualValue(this))
        return true
    }

    override fun get(property: PropertyKey, receiver: JSValue): JSValue {
        if (property.isSymbol)
            return super.get(property, receiver)

        val numericIndex = Operations.canonicalNumericIndexString(property.asValue)
            ?: return super.get(property, receiver)

        return Operations.integerIndexedElementGet(receiver, numericIndex)
    }

    override fun set(property: PropertyKey, value: JSValue, receiver: JSValue): Boolean {
        if (property.isSymbol)
            return super.set(property, value, receiver)

        val numericIndex = Operations.canonicalNumericIndexString(property.asValue)
            ?: return super.set(property, value, receiver)

        Operations.integerIndexedElementSet(receiver, numericIndex, value)
        return true
    }

    override fun delete(property: PropertyKey): Boolean {
        if (property.isSymbol)
            return super.delete(property)

        val numericIndex = Operations.canonicalNumericIndexString(property.asValue)
            ?: return super.delete(property)

        return !Operations.isValidIntegerIndex(this, numericIndex)
    }

    override fun ownPropertyKeys(onlyEnumerable: Boolean): List<PropertyKey> {
        val properties = if (!Operations.isDetachedBuffer(viewedArrayBuffer)) {
            (0..arrayLength).map(PropertyKey::from).toMutableList()
        } else mutableListOf()

        return properties + shape.orderedPropertyTable().filter {
            if (onlyEnumerable) (it.attributes and Descriptor.ENUMERABLE) != 0 else true
        }.map { PropertyKey.from(it.name) }
    }

    companion object {
        fun create(realm: Realm, kind: Operations.TypedArrayKind, proto: JSValue = kind.getProto(realm)) =
            JSIntegerIndexedObject(realm, kind, proto).initialize()
    }
}
