package me.mattco.reeva.runtime.jvmcompat

class TestObject {
    @JvmField
    var jvmField = ":)"

    var ktField = "hello"

    fun test() = 5
}
