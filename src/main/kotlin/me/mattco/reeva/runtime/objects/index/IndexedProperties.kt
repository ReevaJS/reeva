package me.mattco.reeva.runtime.objects.index

import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.index.IndexedStorage.Companion.SPARSE_ARRAY_THRESHOLD
import me.mattco.reeva.runtime.primitives.JSEmpty
import me.mattco.reeva.runtime.primitives.JSUndefined

class IndexedProperties private constructor(
    private var storage: IndexedStorage
) {
    val size: Int
        get() = storage.size
    val arrayLikeSize: Int
        get() = storage.arrayLikeSize

    val isEmpty: Boolean
        get() = size == 0

    constructor() : this(SimpleIndexedStorage())

    fun hasIndex(index: Int) = storage.hasIndex(index)

    fun getDescriptor(index: Int): Descriptor? {
        return storage.get(index)
    }

    fun get(thisValue: JSValue, index: Int): JSValue {
        return storage.get(index)?.getActualValue(thisValue) ?: return JSUndefined
    }

    fun setDescriptor(index: Int, descriptor: Descriptor) {
        if (storage is SimpleIndexedStorage && (index >= SPARSE_ARRAY_THRESHOLD ||
            descriptor.attributes != Descriptor.defaultAttributes ||
            descriptor.hasGetter || descriptor.hasSetter)
        ) {
            switchToGenericStorage()
        }

        storage.set(index, descriptor)
    }

    fun set(thisValue: JSValue, index: Int, descriptor: Descriptor) {
        if (storage is SimpleIndexedStorage && (index >= SPARSE_ARRAY_THRESHOLD || descriptor.attributes != Descriptor.defaultAttributes))
            switchToGenericStorage()

        if (storage is SimpleIndexedStorage) {
            storage.set(index, descriptor)
            return
        }

        val existingValue = storage.get(index) ?: run {
            storage.set(index, descriptor)
            return
        }
        existingValue.setActualValue(thisValue, descriptor.getActualValue(thisValue))
        existingValue.attributes = descriptor.attributes
    }

    fun set(thisValue: JSValue, index: Int, value: JSValue, attributes: Int = Descriptor.defaultAttributes) {
        set(thisValue, index, Descriptor(value, attributes))
    }

    fun remove(index: Int): Boolean {
        val result = storage.get(index) ?: return true
        if (!result.isConfigurable)
            return false
        storage.remove(index)
        return true
    }

    fun insert(index: Int, descriptor: Descriptor) {
        if (storage is SimpleIndexedStorage && (index >= SPARSE_ARRAY_THRESHOLD || descriptor.attributes != Descriptor.defaultAttributes))
            switchToGenericStorage()
        storage.insert(index, descriptor)
    }

    fun insert(index: Int, value: JSValue, attributes: Int = Descriptor.defaultAttributes) {
        insert(index, Descriptor(value, attributes))
    }

    fun removeFirst(thisValue: JSObject?): Descriptor {
        return storage.removeFirst()
    }

    fun removeLast(thisValue: JSObject?): Descriptor {
        return storage.removeLast()
    }

    fun add(descriptor: Descriptor) {
        setDescriptor(arrayLikeSize, descriptor)
    }

    fun addAll(properties: IndexedProperties) {
        if (storage is SimpleIndexedStorage && properties.storage !is SimpleIndexedStorage)
            switchToGenericStorage()

        properties.indices().forEach { index ->
            setDescriptor(index, properties.getDescriptor(index)!!)
        }
    }

    fun setArrayLikeSize(size: Int) {
        if (storage is SimpleIndexedStorage && size > SPARSE_ARRAY_THRESHOLD)
            switchToGenericStorage()
        storage.setArrayLikeSize(size)
    }

    fun indices(): List<Int> {
        val indices = ArrayList<Int>()
        if (storage is SimpleIndexedStorage) {
            indices.ensureCapacity(storage.arrayLikeSize)
            (storage as SimpleIndexedStorage).elements.forEachIndexed { index, value ->
                if (value != JSEmpty)
                    indices.add(index)
            }
        } else {
            (storage as GenericIndexedStorage).also {
                indices.ensureCapacity(it.packedElements.size)
                it.packedElements.forEachIndexed { index, descriptor ->
                    if (descriptor.getRawValue() != JSEmpty)
                        indices.add(index)
                }
                indices.addAll(it.sparseElements.keys.sorted())
            }
        }
        return indices
    }

    fun valuesUnordered(): List<Descriptor> {
        if (storage is SimpleIndexedStorage)
            return (storage as SimpleIndexedStorage).elements.map { Descriptor(it, Descriptor.defaultAttributes) }

        (storage as GenericIndexedStorage).also {
            val values = it.packedElements.toMutableList()
            for (value in it.sparseElements.values)
                values.add(value)
            return values
        }
    }

    fun iterator(startingIndex: Int = 0, skipEmpty: Boolean = true) = IndexedPropertyIterator(this, startingIndex, skipEmpty)

    private fun switchToGenericStorage() {
        storage = GenericIndexedStorage(storage as SimpleIndexedStorage)
    }
}
