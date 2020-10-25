package me.mattco.reeva.runtime.values.objects.index

import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.objects.Descriptor
import me.mattco.reeva.runtime.values.objects.index.IndexedStorage.Companion.MIN_PACKED_RESIZE_AMOUNT
import me.mattco.reeva.runtime.values.objects.index.IndexedStorage.Companion.SPARSE_ARRAY_THRESHOLD
import me.mattco.reeva.runtime.values.primitives.JSEmpty
import me.mattco.reeva.utils.expect

class GenericIndexedStorage(simpleStorage: SimpleIndexedStorage) : IndexedStorage {
    internal val packedElements = ArrayList<Descriptor>()
    internal var sparseElements = mutableMapOf<Int, Descriptor>()
    private var sizeBacker = 0

    override val size: Int
        get() = packedElements.size + sparseElements.size
    override val arrayLikeSize: Int
        get() = sizeBacker

    init {
        sizeBacker = simpleStorage.arrayLikeSize
        simpleStorage.elements.forEach { element ->
            packedElements.add(Descriptor(element, Descriptor.defaultAttributes))
        }
    }

    override fun hasIndex(index: Int): Boolean {
        if (index < SPARSE_ARRAY_THRESHOLD)
            return index < packedElements.size && packedElements[index].getRawValue() !is JSEmpty
        return index in sparseElements
    }

    override fun get(index: Int): Descriptor? {
        if (index >= sizeBacker)
            return null
        if (index < SPARSE_ARRAY_THRESHOLD)
            return packedElements.getOrNull(index)
        return sparseElements[index]
    }

    override fun set(index: Int, value: JSValue, attributes: Int) {
        if (index >= sizeBacker)
            sizeBacker = index + 1
        if (index < SPARSE_ARRAY_THRESHOLD) {
            if (index > packedElements.lastIndex) {
                val minCapacity = (index + MIN_PACKED_RESIZE_AMOUNT).coerceAtMost(SPARSE_ARRAY_THRESHOLD)
                repeat(minCapacity - packedElements.size) {
                    packedElements.add(Descriptor(JSEmpty, 0))
                }
            }
            packedElements[index] = Descriptor(value, attributes)
        } else {
            sparseElements[index] = Descriptor(value, attributes)
        }
    }

    override fun remove(index: Int) {
        if (index >= sizeBacker)
            return
        if (index + 1 == sizeBacker) {
            removeLast()
            return
        }

        if (index < SPARSE_ARRAY_THRESHOLD) {
            if (index < packedElements.size)
                packedElements[index].setRawValue(JSEmpty)
        } else {
            sparseElements.remove(index)
        }
    }

    override fun insert(index: Int, value: JSValue, attributes: Int) {
        if (index >= sizeBacker) {
            set(index, value, attributes)
            return
        }

        sizeBacker++

        if (sparseElements.isNotEmpty()) {
            val newMap = LinkedHashMap<Int, Descriptor>(sparseElements.size + 1)
            sparseElements.forEach { (oldIndex, descriptor) ->
                val newIndex = if (oldIndex >= index) oldIndex + 1 else oldIndex
                newMap[newIndex] = descriptor
            }
            sparseElements = newMap
        }

        if (index < SPARSE_ARRAY_THRESHOLD) {
            packedElements.add(index, Descriptor(value, attributes))
        } else {
            sparseElements[index] = Descriptor(value, attributes)
        }
    }

    override fun removeFirst(): Descriptor {
        expect(sizeBacker > 0)
        sizeBacker--

        if (sparseElements.isNotEmpty()) {
            val newMap = LinkedHashMap<Int, Descriptor>(sparseElements.size + 1)
            sparseElements.forEach { (oldIndex, descriptor) ->
                newMap[oldIndex - 1] = descriptor
            }
            sparseElements = newMap
        }

        return packedElements.removeFirst()
    }

    override fun removeLast(): Descriptor {
        expect(sizeBacker > 0)
        sizeBacker--

        return if (sizeBacker <= SPARSE_ARRAY_THRESHOLD) {
            val last = packedElements[sizeBacker]
            packedElements[sizeBacker].setRawValue(JSEmpty)
            last
        } else {
            val result = sparseElements[sizeBacker]
            sparseElements.remove(sizeBacker)
            expect(result != null)
            result
        }
    }

    override fun setArrayLikeSize(size: Int) {
        sizeBacker = size
        if (size < SPARSE_ARRAY_THRESHOLD) {
            repeat(size - packedElements.size) {
                packedElements.add(Descriptor(JSEmpty, 0))
            }
            sparseElements.clear()
        } else {
            val it = sparseElements.entries.iterator()
            while (it.hasNext()) {
                val next = it.next()
                if (next.key >= size)
                    it.remove()
            }
        }
    }
}
