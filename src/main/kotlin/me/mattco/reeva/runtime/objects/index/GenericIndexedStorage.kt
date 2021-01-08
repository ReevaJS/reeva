package me.mattco.reeva.runtime.objects.index

import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.index.IndexedStorage.Companion.INDEX_UPPER_BOUND
import me.mattco.reeva.runtime.objects.index.IndexedStorage.Companion.MIN_PACKED_RESIZE_AMOUNT
import me.mattco.reeva.runtime.objects.index.IndexedStorage.Companion.SPARSE_ARRAY_THRESHOLD
import me.mattco.reeva.runtime.primitives.JSEmpty
import me.mattco.reeva.utils.expect

class GenericIndexedStorage(simpleStorage: SimpleIndexedStorage) : IndexedStorage {
    internal val packedElements = ArrayList<Descriptor>()
    internal var sparseElements = mutableMapOf<Int, Descriptor>()

    // Numeric indices should cover the range [0, 2^32), but of course
    // Int only covers [0, 2^31), which is exactly half of the required
    // range. This covers the elements not covered by sparseElements above, i.e.,
    // [2^31, 2^32).
    internal var longElements = mutableMapOf<Int, Descriptor>()

    private var sizeBacker = 0L

    override val size: Long
        get() = packedElements.size.toLong() + sparseElements.size.toLong()
    override val arrayLikeSize: Long
        get() = sizeBacker

    init {
        sizeBacker = simpleStorage.arrayLikeSize
        simpleStorage.elements.forEach { element ->
            packedElements.add(Descriptor(element, Descriptor.defaultAttributes))
        }
    }

    override fun hasIndex(index: Int): Boolean {
        if (index < SPARSE_ARRAY_THRESHOLD)
            return index < packedElements.size && packedElements[index].getRawValue() != JSEmpty
        return index in sparseElements
    }

    override fun hasIndex(index: Long): Boolean {
        if (index <= Int.MAX_VALUE)
            return hasIndex(index.toInt())
        return trimLongIndex(index) in longElements
    }

    override fun get(index: Int): Descriptor? {
        if (!hasIndex(index))
            return null
        if (index < SPARSE_ARRAY_THRESHOLD)
            return packedElements.getOrNull(index)
        return sparseElements[index]
    }

    override fun get(index: Long): Descriptor? {
        if (!hasIndex(index))
            return null
        if (index <= Int.MAX_VALUE)
            return get(index.toInt())
        if (index >= sizeBacker)
            return null
        return longElements[trimLongIndex(index)]
    }

    override fun set(index: Int, descriptor: Descriptor) {
        if (index >= sizeBacker)
            sizeBacker = index + 1L
        if (index < SPARSE_ARRAY_THRESHOLD) {
            if (index > packedElements.lastIndex) {
                val minCapacity = (index + MIN_PACKED_RESIZE_AMOUNT).coerceAtMost(SPARSE_ARRAY_THRESHOLD)
                repeat(minCapacity - packedElements.size) {
                    packedElements.add(Descriptor(JSEmpty, 0))
                }
            }
            packedElements[index] = descriptor
        } else {
            sparseElements[index] = descriptor
        }
    }

    override fun set(index: Long, descriptor: Descriptor) {
        if (index <= Int.MAX_VALUE)
            return set(index.toInt(), descriptor)
        if (index >= sizeBacker)
            sizeBacker = index + 1
        longElements[trimLongIndex(index)] = descriptor
    }

    override fun remove(index: Int) {
        if (index >= sizeBacker)
            return

        if (index < SPARSE_ARRAY_THRESHOLD) {
            if (index < packedElements.size)
                packedElements[index].setRawValue(JSEmpty)
        } else {
            sparseElements.remove(index)
        }
    }

    override fun remove(index: Long) {
        if (index <= Int.MAX_VALUE)
            return remove(index.toInt())
        if (index >= sizeBacker)
            return

        longElements.remove(trimLongIndex(index))
    }

    override fun insert(index: Int, descriptor: Descriptor) {
        if (index >= sizeBacker) {
            set(index, descriptor)
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
            packedElements.add(index, descriptor)
        } else {
            sparseElements[index] = descriptor
        }
    }

    override fun insert(longIndex: Long, descriptor: Descriptor) {
        if (longIndex <= Int.MAX_VALUE)
            return insert(longIndex.toInt(), descriptor)
        if (longIndex >= sizeBacker) {
            set(longIndex, descriptor)
            return
        }

        sizeBacker++
        val index = trimLongIndex(longIndex)

        if (longElements.isNotEmpty()) {
            val newMap = LinkedHashMap<Int, Descriptor>(longElements.size + 1)
            longElements.forEach { (oldIndex, descriptor) ->
                val newIndex = if (oldIndex >= index) oldIndex + 1 else oldIndex
                newMap[newIndex] = descriptor
            }
            longElements = newMap
        }

        longElements[index] = descriptor
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

        return when {
            sizeBacker <= SPARSE_ARRAY_THRESHOLD -> {
                val intSize = sizeBacker.toInt()
                val result = packedElements[intSize]
                packedElements[intSize].setRawValue(JSEmpty)
                result
            }
            sizeBacker in Int.MIN_VALUE..Int.MAX_VALUE -> {
                val intSize = sizeBacker.toInt()
                val result = sparseElements[intSize]
                sparseElements.remove(intSize)
                expect(result != null)
                result
            }
            else -> {
                val longIndex = trimLongIndex(sizeBacker)
                val result = longElements[longIndex]
                longElements.remove(longIndex)
                expect(result != null)
                result
            }
        }
    }

    override fun setArrayLikeSize(size: Long) {
        if (size < SPARSE_ARRAY_THRESHOLD) {
            repeat(size.toInt() - packedElements.size) {
                packedElements.add(Descriptor(JSEmpty, 0))
            }
            sparseElements.clear()
        } else if (size < sizeBacker) {
            listOf(sparseElements.entries.iterator(), longElements.entries.iterator()).forEach {
                while (it.hasNext()) {
                    val next = it.next()
                    if (next.key >= size)
                        it.remove()
                }
            }
        }
        sizeBacker = size
    }

    companion object {
        private fun trimLongIndex(length: Long): Int {
            expect(length in (Int.MAX_VALUE + 1L)..INDEX_UPPER_BOUND)
            return (length - Int.MAX_VALUE).toInt()
        }
    }
}
