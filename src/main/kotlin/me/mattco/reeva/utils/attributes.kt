package me.mattco.reeva.utils

import me.mattco.reeva.runtime.objects.Descriptor

class Attrs {
    inner class Attribute(private val withValue: Int, private val withoutValue: Int) {
        operator fun unaryPlus() = withValue
        operator fun unaryMinus() = withoutValue

        operator fun plus(other: Attribute) = unaryPlus()
        operator fun minus(other: Attribute) = unaryMinus()
    }

    val conf = Attribute(Descriptor.CONFIGURABLE, Descriptor.HAS_CONFIGURABLE)
    val enum = Attribute(Descriptor.ENUMERABLE, Descriptor.HAS_ENUMERABLE)
    val writ = Attribute(Descriptor.WRITABLE, Descriptor.HAS_WRITABLE)
}
