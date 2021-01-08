package me.mattco.reeva.runtime.objects.index

import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.primitives.JSEmpty
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.unreachable

class SimpleIndexedStorage : IndexedStorage {
    internal val elements = ArrayList<JSValue>()
    private var sizeBacker = 0L

    override val size: Long
        get() = elements.size.toLong()
    override val arrayLikeSize: Long
        get() = sizeBacker

    override fun hasIndex(index: Int) = index < sizeBacker && elements[index] != JSEmpty

    override fun hasIndex(index: Long) = unreachable()

    override fun get(index: Int) = if (hasIndex(index)) {
        Descriptor(elements[index], Descriptor.DEFAULT_ATTRIBUTES)
    } else null

    override fun get(index: Long) = unreachable()

    override fun set(index: Int, descriptor: Descriptor) {
        expect(descriptor.attributes == Descriptor.DEFAULT_ATTRIBUTES)
        expect(!descriptor.hasGetterFunction && !descriptor.hasSetterFunction)
        expect(index < IndexedStorage.SPARSE_ARRAY_THRESHOLD)

        if (index >= sizeBacker) {
            sizeBacker = index + 1L
            val minCapacity = (index + IndexedStorage.MIN_PACKED_RESIZE_AMOUNT).coerceAtMost(IndexedStorage.SPARSE_ARRAY_THRESHOLD)
            repeat(minCapacity - elements.size) {
                elements.add(JSEmpty)
            }
        }
        elements[index] = descriptor.getRawValue()
    }

    override fun set(index: Long, descriptor: Descriptor) = unreachable()

    override fun remove(index: Int) {
        if (index <= elements.lastIndex)
            elements[index] = JSEmpty
    }

    override fun remove(index: Long) = unreachable()

    override fun insert(index: Int, descriptor: Descriptor) {
        expect(descriptor.attributes == Descriptor.DEFAULT_ATTRIBUTES)
        expect(!descriptor.hasGetterFunction && !descriptor.hasSetterFunction)
        expect(index < IndexedStorage.SPARSE_ARRAY_THRESHOLD)
        sizeBacker++
        elements.add(index, descriptor.getRawValue())
    }

    override fun insert(index: Long, descriptor: Descriptor) = unreachable()

    override fun removeFirst(): Descriptor {
        sizeBacker--
        return Descriptor(elements.removeFirst(), Descriptor.DEFAULT_ATTRIBUTES)
    }

    override fun removeLast(): Descriptor {
        sizeBacker--
        return Descriptor(elements.removeLast(), Descriptor.DEFAULT_ATTRIBUTES)
    }

    override fun setArrayLikeSize(size: Long) {
        expect(size <= IndexedStorage.SPARSE_ARRAY_THRESHOLD)
        if (size > elements.size.toLong()) {
            repeat((size - elements.size.toLong()).toInt()) {
                elements.add(JSEmpty)
            }
        } else if (size < sizeBacker) {
            for (i in elements.lastIndex downTo size.toInt()) {
                elements.removeAt(i)
            }
        }
        sizeBacker = size
    }
}
