package me.mattco.reeva.jvmcompat

class TestObject {
    @JvmField
    var jvmField = ":)"

    var ktField = "hello"

    fun test() = 5
}
