package com.example

abstract class MyClass {
    @JvmField
    val a: Int

    @JvmField
    val b: Int

    constructor(a: Int, b: Int) {
        this.a = a
        this.b = b
    }

    abstract fun getValue(): Int

    companion object {
        @JvmField
        val staticProperty = 10
    }
}
