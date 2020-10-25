package me.mattco.reeva.runtime.values.objects.index

import me.mattco.reeva.runtime.Agent.Companion.checkError
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.objects.Descriptor
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.objects.index.IndexedStorage.Companion.SPARSE_ARRAY_THRESHOLD
import me.mattco.reeva.runtime.values.primitives.JSAccessor
import me.mattco.reeva.runtime.values.primitives.JSEmpty
import me.mattco.reeva.utils.expect

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

    fun get(thisValue: JSObject?, index: Int, evaluateAccessors: Boolean = true): Descriptor? {
        val result = storage.get(index) ?: return null
        if (!evaluateAccessors)
            return result
        val value = result.value
        if (value is JSAccessor) {
            expect(thisValue != null)
            return Descriptor(value.callGetter(thisValue), result.attributes)
        }
        return result
    }

    fun set(thisValue: JSObject?, index: Int, value: JSValue, attributes: Int = Descriptor.defaultAttributes, evaluateAccessors: Boolean = true) {
        if (storage is SimpleIndexedStorage && (index >= SPARSE_ARRAY_THRESHOLD || attributes != Descriptor.defaultAttributes))
            switchToGenericStorage()
        if (storage is SimpleIndexedStorage || !evaluateAccessors) {
            storage.set(index, value, attributes)
            return
        }

        val existingValue = storage.get(index)
        if (existingValue != null && existingValue.value is JSAccessor) {
            expect(thisValue != null)
            (existingValue.value as JSAccessor).callSetter(thisValue, value)
        } else {
            storage.set(index, value, attributes)
        }
    }

    fun remove(index: Int): Boolean {
        val result = storage.get(index) ?: return true
        if (!result.isConfigurable)
            return false
        storage.remove(index)
        return true
    }

    fun insert(index: Int, value: JSValue, attributes: Int = Descriptor.defaultAttributes) {
        if (storage is SimpleIndexedStorage && (index >= SPARSE_ARRAY_THRESHOLD || attributes != Descriptor.defaultAttributes))
            switchToGenericStorage()
        storage.insert(index, value, attributes)
    }

    fun removeFirst(thisValue: JSObject?): Descriptor {
        val first = storage.removeFirst()
        if (first.value is JSAccessor) {
            expect(thisValue != null)
            return Descriptor((first.value as JSAccessor).callGetter(thisValue), first.attributes)
        }
        return first
    }

    fun removeLast(thisValue: JSObject?): Descriptor {
        val last = storage.removeLast()
        if (last.value is JSAccessor) {
            expect(thisValue != null)
            return Descriptor((last.value as JSAccessor).callGetter(thisValue), last.attributes)
        }
        return last
    }

    fun add(value: JSValue, attributes: Int = Descriptor.defaultAttributes) {
        set(null, arrayLikeSize, value, attributes)
    }

    fun addAll(thisValue: JSObject, properties: IndexedProperties, evaluateAccessors: Boolean = true) {
        if (storage is SimpleIndexedStorage && properties.storage !is SimpleIndexedStorage)
            switchToGenericStorage()

        properties.indices().forEach { index ->
            val element = properties.get(thisValue, index, evaluateAccessors)!!
            checkError() ?: return
            storage.set(storage.arrayLikeSize, element.value, element.attributes)
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
                    if (descriptor.value != JSEmpty)
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
