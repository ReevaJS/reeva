package com.reevajs.reeva.runtime.memory

import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.utils.ecmaAssert
import java.nio.ByteBuffer

class DataBlock(val size: Int) {
    private val buffer = ByteBuffer.allocate(size)

    operator fun get(index: Int): Byte = buffer[index]

    fun getBytes(index: Int, length: Int) = ByteArray(length).also {
        buffer.position(index)
        buffer.get(it, 0, length)
    }

    fun getShort(index: Int) = buffer.getShort(index)

    fun getChar(index: Int) = buffer.getChar(index)

    fun getInt(index: Int) = buffer.getInt(index)

    fun getLong(index: Int) = buffer.getLong(index)

    fun getFloat(index: Int) = buffer.getFloat(index)

    fun getDouble(index: Int) = buffer.getDouble(index)

    operator fun set(index: Int, value: Byte) {
        buffer.put(index, value)
    }

    fun setShort(index: Int, value: Short) = apply {
        buffer.putShort(index, value)
    }

    fun setChar(index: Int, value: Char) = apply {
        buffer.putChar(index, value)
    }

    fun setInt(index: Int, value: Int) = apply {
        buffer.putInt(index, value)
    }

    fun setLong(index: Int, value: Long) = apply {
        buffer.putLong(index, value)
    }

    fun setFloat(index: Int, value: Float) = apply {
        buffer.putFloat(index, value)
    }

    fun setDouble(index: Int, value: Double) = apply {
        buffer.putDouble(index, value)
    }

    @ECMAImpl("6.2.8.3")
    fun copyFrom(fromBlock: DataBlock, fromIndex: Int, toIndex: Int, count: Int) {
        ecmaAssert(fromBlock !== this)
        copyFrom(fromBlock.buffer.array(), fromIndex, toIndex, count)
    }

    fun copyFrom(fromArr: ByteArray, fromIndex: Int, toIndex: Int, count: Int) {
        ecmaAssert(fromIndex + count <= fromArr.size)
        ecmaAssert(toIndex + count <= size)

        buffer.position(toIndex)
        buffer.put(fromArr, fromIndex, count)
    }
}
