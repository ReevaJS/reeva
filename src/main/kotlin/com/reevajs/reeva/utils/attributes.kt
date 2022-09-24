package com.reevajs.reeva.utils

import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.objects.Descriptor

fun JSValue.toDescriptor(block: Attrs.() -> Unit = {}): Descriptor {
    return Descriptor(this, Attrs().apply(block).attrs)
}

fun attrs(block: Attrs.() -> Unit): Int {
    return Attrs().apply(block).attrs
}

class Attrs {
    var attrs = 0

    inner class Attribute(private val withValue: Int, private val withoutValue: Int) {
        operator fun unaryPlus() {
            attrs = attrs or withValue
        }

        operator fun unaryMinus() {
            attrs = attrs or withoutValue
        }
    }

    val conf = Attribute(Descriptor.CONFIGURABLE, Descriptor.HAS_CONFIGURABLE)
    val enum = Attribute(Descriptor.ENUMERABLE, Descriptor.HAS_ENUMERABLE)
    val writ = Attribute(Descriptor.WRITABLE, Descriptor.HAS_WRITABLE)
}
