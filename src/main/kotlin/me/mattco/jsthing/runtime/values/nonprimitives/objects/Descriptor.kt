package me.mattco.jsthing.runtime.values.nonprimitives.objects

import me.mattco.jsthing.runtime.values.JSValue
import me.mattco.jsthing.runtime.values.nonprimitives.functions.JSFunction

data class Descriptor(
    var value: JSValue,
    val attributes: Attributes,
    var getter: JSFunction? = null,
    var setter: JSFunction? = null
) {
    val isAccessorDescriptor: Boolean
        get() = getter != null || setter != null

    val isDataDescriptor: Boolean
        get() = !value.isEmpty || attributes.hasWritable

    val isGenericDescriptor: Boolean
        get() = !isAccessorDescriptor && !isDataDescriptor

    companion object {
        fun fromObject(obj: JSObject): Descriptor {
            TODO()
        }
    }
}
