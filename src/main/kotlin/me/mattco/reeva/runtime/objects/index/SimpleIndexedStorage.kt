package me.mattco.reeva.runtime.objects.index

import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.primitives.JSEmpty
import me.mattco.reeva.utils.expect

class SimpleIndexedStorage : IndexedStorage {
    internal val elements = ArrayList<JSValue>()
    private var sizeBacker = 0

    override val size: Int
        get() = elements.size
    override val arrayLikeSize: Int
        get() = sizeBacker

    override fun hasIndex(index: Int) = index < sizeBacker && elements[index] != JSEmpty

    override fun get(index: Int) = if (hasIndex(index)) {
        Descriptor(elements[index], Descriptor.defaultAttributes)
    } else null

    override fun set(index: Int, descriptor: Descriptor) {
        expect(descriptor.attributes == Descriptor.defaultAttributes)
        expect(!descriptor.hasGetterFunction && !descriptor.hasSetterFunction)
        expect(index < IndexedStorage.SPARSE_ARRAY_THRESHOLD)

        if (index >= sizeBacker) {
            sizeBacker = index + 1
            val minCapacity = (index + IndexedStorage.MIN_PACKED_RESIZE_AMOUNT).coerceAtMost(IndexedStorage.SPARSE_ARRAY_THRESHOLD)
            repeat(minCapacity - elements.size) {
                elements.add(JSEmpty)
            }
        }
        elements[index] = descriptor.getRawValue()
    }

    override fun remove(index: Int) {
        if (index <= elements.lastIndex)
            elements[index] = JSEmpty
    }

    override fun insert(index: Int, descriptor: Descriptor) {
        expect(descriptor.attributes == Descriptor.defaultAttributes)
        expect(!descriptor.hasGetterFunction && !descriptor.hasSetterFunction)
        expect(index < IndexedStorage.SPARSE_ARRAY_THRESHOLD)
        sizeBacker++
        elements.add(index, descriptor.getRawValue())
    }

    override fun removeFirst(): Descriptor {
        sizeBacker--
        return Descriptor(elements.removeFirst(), Descriptor.defaultAttributes)
    }

    override fun removeLast(): Descriptor {
        sizeBacker--
        return Descriptor(elements.removeLast(), Descriptor.defaultAttributes)
    }

    override fun setArrayLikeSize(size: Int) {
        expect(size <= IndexedStorage.SPARSE_ARRAY_THRESHOLD)
        sizeBacker = size
        repeat(size - elements.size) {
            elements.add(JSEmpty)
        }
    }
}
