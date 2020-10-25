package me.mattco.reeva.runtime.values.objects

import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.functions.JSFunction
import me.mattco.reeva.runtime.values.primitives.JSAccessor
import me.mattco.reeva.runtime.values.primitives.JSEmpty
import me.mattco.reeva.runtime.values.primitives.JSNativeProperty
import me.mattco.reeva.runtime.values.primitives.JSUndefined
import me.mattco.reeva.utils.toValue

data class Descriptor(
    private var value: JSValue,
    var attributes: Int,
    var getter: JSFunction? = null,
    var setter: JSFunction? = null
) {
    @ECMAImpl("IsAccessorDescriptor", "6.2.5.1")
    val isAccessorDescriptor: Boolean
        get() = getter != null || setter != null

    @ECMAImpl("IsDataDescriptor", "6.2.5.2")
    val isDataDescriptor: Boolean
        get() = !value.isEmpty || hasWritable

    @ECMAImpl("IsGenericDescriptor", "6.2.5.3")
    val isGenericDescriptor: Boolean
        get() = !isAccessorDescriptor && !isDataDescriptor

    val isEmpty: Boolean
        get() = value == JSUndefined && attributes == 0 && getter == null && setter == null


    val hasConfigurable: Boolean
        get() = attributes and HAS_CONFIGURABLE != 0

    val hasEnumerable: Boolean
        get() = attributes and HAS_ENUMERABLE != 0

    val hasWritable: Boolean
        get() = attributes and HAS_WRITABLE != 0

    val hasGetter: Boolean
        get() = attributes and HAS_GETTER != 0

    val hasSetter: Boolean
        get() = attributes and HAS_SETTER != 0

    val isConfigurable: Boolean
        get() = attributes and CONFIGURABLE != 0

    val isEnumerable: Boolean
        get() = attributes and ENUMERABLE != 0

    val isWritable: Boolean
        get() = attributes and WRITABLE != 0

    init {
        if (attributes and CONFIGURABLE != 0)
            attributes = attributes or HAS_CONFIGURABLE
        if (attributes and ENUMERABLE != 0)
            attributes = attributes or HAS_ENUMERABLE
        if (attributes and WRITABLE != 0)
            attributes = attributes or HAS_WRITABLE
    }

    fun setHasConfigurable() = apply {
        attributes = attributes or HAS_CONFIGURABLE
    }

    fun setHasEnumerable() = apply {
        attributes = attributes or HAS_ENUMERABLE
    }

    fun setHasWritable() = apply {
        attributes = attributes or HAS_WRITABLE
    }

    fun setHasGetter() = apply {
        attributes = attributes or HAS_GETTER
    }

    fun setHasSetter() = apply {
        attributes = attributes or HAS_SETTER
    }

    fun setConfigurable(configurable: Boolean = true) = apply {
        attributes = if (configurable) {
            attributes or CONFIGURABLE
        } else {
            attributes and CONFIGURABLE.inv()
        }
    }

    fun setEnumerable(enumerable: Boolean = true) = apply {
        attributes = if (enumerable) {
            attributes or ENUMERABLE
        } else {
            attributes and ENUMERABLE.inv()
        }
    }

    fun setWritable(writable: Boolean = true) = apply {
        attributes = if (writable) {
            attributes or WRITABLE
        } else {
            attributes and WRITABLE.inv()
        }
    }

    fun getActualValue(thisValue: JSValue?): JSValue {
        return when (val v = value) {
            is JSNativeProperty -> v.get(thisValue!!)
            is JSAccessor -> v.callGetter(thisValue!!)
            else -> v
        }
    }

    fun setActualValue(thisValue: JSValue?, newValue: JSValue) {
        when (val v = value) {
            is JSNativeProperty -> v.set(thisValue!!, newValue)
            is JSAccessor -> v.callSetter(thisValue!!, newValue)
            else -> value = newValue
        }
    }

    fun getRawValue(): JSValue = value

    fun setRawValue(value: JSValue) {
        this.value = value
    }

    @ECMAImpl("FromPropertyDescriptor", "6.2.5.4")
    fun toObject(realm: Realm, thisValue: JSValue): JSObject {
        val obj = JSObject.create(realm)
        if (isAccessorDescriptor) {
            if (getter != null)
                obj.set("get", getter!!)
            if (setter != null)
                obj.set("set", setter!!)
        } else if (isDataDescriptor) {
            obj.set("value", getActualValue(thisValue))
        }

        if (hasConfigurable)
            obj.set("configurable", isConfigurable.toValue())
        if (hasEnumerable)
            obj.set("enumerable", isEnumerable.toValue())
        if (hasWritable)
            obj.set("writable", isWritable.toValue())

        return obj
    }

    companion object {
        const val CONFIGURABLE = 1 shl 0
        const val ENUMERABLE = 1 shl 1
        const val WRITABLE = 1 shl 2
        const val HAS_GETTER = 1 shl 3
        const val HAS_SETTER = 1 shl 4
        const val HAS_CONFIGURABLE = 1 shl 5
        const val HAS_ENUMERABLE = 1 shl 6
        const val HAS_WRITABLE = 1 shl 7

        const val defaultAttributes = CONFIGURABLE or ENUMERABLE or WRITABLE or HAS_CONFIGURABLE or HAS_ENUMERABLE or HAS_WRITABLE

        @ECMAImpl("ToPropertyDescriptor", "6.2.5.5")
        fun fromObject(obj: JSObject): Descriptor {
            TODO()
        }
    }
}
