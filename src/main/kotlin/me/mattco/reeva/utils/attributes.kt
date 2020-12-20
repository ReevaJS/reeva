package me.mattco.reeva.utils

import me.mattco.reeva.runtime.objects.Descriptor
fun attrs(block: Attrs.() -> Int): Int {
    return Attrs().block()
}

class Attrs {
    inner class Attribute(val withValue: Int, val withoutValue: Int) {
        operator fun unaryPlus() = withValue
        operator fun unaryMinus() = withoutValue

        operator fun plus(other: Attribute) = unaryPlus()
        operator fun minus(other: Attribute) = unaryMinus()
    }

    operator fun Int.plus(other: Attribute) = this or other.withValue
    operator fun Int.minus(other: Attribute) = this or other.withoutValue

    val conf = Attribute(Descriptor.CONFIGURABLE, Descriptor.HAS_CONFIGURABLE)
    val enum = Attribute(Descriptor.ENUMERABLE, Descriptor.HAS_ENUMERABLE)
    val writ = Attribute(Descriptor.WRITABLE, Descriptor.HAS_WRITABLE)
}
