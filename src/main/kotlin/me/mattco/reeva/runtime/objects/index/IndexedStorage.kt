package me.mattco.reeva.runtime.objects.index

import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.Descriptor

interface IndexedStorage {
    val size: Long
    val arrayLikeSize: Long

    fun hasIndex(index: Int): Boolean
    fun hasIndex(index: Long): Boolean
    fun get(index: Int): Descriptor?
    fun get(index: Long): Descriptor?
    fun set(index: Int, descriptor: Descriptor)
    fun set(index: Long, descriptor: Descriptor)
    fun remove(index: Int)
    fun remove(index: Long)

    fun insert(index: Int, descriptor: Descriptor)
    fun insert(index: Long, descriptor: Descriptor)
    fun removeFirst(): Descriptor
    fun removeLast(): Descriptor

    fun setArrayLikeSize(size: Long)

    companion object {
        const val MIN_PACKED_RESIZE_AMOUNT = 20
        const val SPARSE_ARRAY_THRESHOLD = 200
        const val INDEX_UPPER_BOUND = Int.MAX_VALUE * 2L
    }
}
