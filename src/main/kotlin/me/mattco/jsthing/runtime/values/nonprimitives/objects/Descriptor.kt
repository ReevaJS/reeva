package me.mattco.jsthing.runtime.values.nonprimitives.objects

import me.mattco.jsthing.runtime.annotations.ECMAImpl
import me.mattco.jsthing.runtime.values.JSValue
import me.mattco.jsthing.runtime.values.nonprimitives.functions.JSFunction
import me.mattco.jsthing.runtime.values.primitives.JSUndefined

data class Descriptor(
    var value: JSValue,
    val attributes: Attributes,
    var getter: JSFunction? = null,
    var setter: JSFunction? = null
) {
    @ECMAImpl("IsAccessorDescriptor", "6.2.5.1")
    val isAccessorDescriptor: Boolean
        get() = getter != null || setter != null

    @ECMAImpl("IsDataDescriptor", "6.2.5.2")
    val isDataDescriptor: Boolean
        get() = !value.isEmpty || attributes.hasWritable

    @ECMAImpl("IsGenericDescriptor", "6.2.5.3")
    val isGenericDescriptor: Boolean
        get() = !isAccessorDescriptor && !isDataDescriptor

    val isEmpty: Boolean
        get() = value == JSUndefined && attributes.num == 0 && getter == null && setter == null

    @ECMAImpl("FromPropertyDescriptor", "6.2.5.4")
    fun toObject(): JSObject {
        TODO()
    }

    companion object {
        @ECMAImpl("ToPropertyDescriptor", "6.2.5.5")
        fun fromObject(obj: JSObject): Descriptor {
            TODO()
        }
    }
}
