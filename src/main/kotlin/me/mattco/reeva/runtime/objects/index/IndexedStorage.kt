package me.mattco.reeva.runtime.objects.index

import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.Descriptor

interface IndexedStorage {
    val size: Int
    val arrayLikeSize: Int

    fun hasIndex(index: Int): Boolean
    fun get(index: Int): Descriptor?
    fun set(index: Int, descriptor: Descriptor)
    fun remove(index: Int)

    fun insert(index: Int, descriptor: Descriptor)
    fun removeFirst(): Descriptor
    fun removeLast(): Descriptor

    fun setArrayLikeSize(size: Int)

    companion object {
        const val MIN_PACKED_RESIZE_AMOUNT = 20
        const val SPARSE_ARRAY_THRESHOLD = 200
    }
}
