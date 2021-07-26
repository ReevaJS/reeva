package com.reevajs.reeva.jvmcompat

open class TestObject(@JvmField private val ctorArg: String) {
    @JvmField
    var jvmField = ":)"

    var ktField = "hello"

    fun test() = 5

    companion object {
        @JvmStatic
        fun thing(obj: TestInterface) {
            println("number: ${obj.getNumber()}")
            println("obj is TestObject: ${obj is TestObject}")
            if (obj is TestObject) {
                println("test object number: ${obj.test()}")
                println("ctor arg: ${obj.ctorArg}")
            }
        }
    }
}
