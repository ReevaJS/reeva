package me.mattco.reeva.runtime.objects

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.primitives.*
import me.mattco.reeva.utils.throwTypeError
import me.mattco.reeva.utils.toValue

data class Descriptor(
    private var value: JSValue,
    var attributes: Int,
    var getter: JSValue = JSEmpty,
    var setter: JSValue = JSEmpty
) {
    @ECMAImpl("6.2.5.1")
    val isAccessorDescriptor: Boolean
        get() = getter != JSEmpty || setter != JSEmpty

    @ECMAImpl("6.2.5.2")
    val isDataDescriptor: Boolean
        get() = !value.isEmpty || hasWritable

    @ECMAImpl("6.2.5.3")
    val isGenericDescriptor: Boolean
        get() = !isAccessorDescriptor && !isDataDescriptor

    val isEmpty: Boolean
        get() = value == JSUndefined && attributes == 0 && !hasGetter && !hasSetter


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

    @ECMAImpl("6.2.5.4", "FromPropertyDescriptor")
    fun toObject(realm: Realm, thisValue: JSValue): JSObject {
        val obj = JSObject.create(realm)
        if (isAccessorDescriptor) {
            if (hasGetter)
                obj.set("get", getter)
            if (hasSetter)
                obj.set("set", setter)
        } else if (isDataDescriptor) {
            obj.set("value", getActualValue(thisValue))
        }

        obj.set("configurable", isConfigurable.toValue())
        obj.set("enumerable", isEnumerable.toValue())
        if (isDataDescriptor)
            obj.set("writable", isWritable.toValue())

        return obj
    }

    @ECMAImpl("6.2.5.6")
    fun complete() = apply {
        if (isGenericDescriptor) {
            if (value == JSEmpty)
                value = JSUndefined
            if (!hasWritable)
                attributes = attributes or HAS_WRITABLE
        }

        if (!hasConfigurable)
            attributes = attributes or HAS_CONFIGURABLE
        if (!hasEnumerable)
            attributes = attributes or HAS_ENUMERABLE
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
        const val HAS_BASIC = HAS_CONFIGURABLE or HAS_ENUMERABLE or HAS_WRITABLE

        const val defaultAttributes = CONFIGURABLE or ENUMERABLE or WRITABLE or HAS_BASIC

        @JSThrows
        @ECMAImpl("6.2.5.5", "ToPropertyDescriptor")
        fun fromObject(obj: JSValue): Descriptor {
            if (obj !is JSObject)
                throwTypeError("TODO: message")

            val descriptor = Descriptor(JSEmpty, 0)
            if (obj.hasProperty("enumerable")) {
                descriptor.setHasEnumerable()
                descriptor.setEnumerable(Operations.toBoolean(obj.get("enumerable")) == JSTrue)
            }

            if (obj.hasProperty("configurable")) {
                descriptor.setHasConfigurable()
                descriptor.setConfigurable(Operations.toBoolean(obj.get("configurable")) == JSTrue)
            }

            if (obj.hasProperty("writable")) {
                descriptor.setHasWritable()
                descriptor.setWritable(Operations.toBoolean(obj.get("writable")) == JSTrue)
            }

            if (obj.hasProperty("value")) {
                descriptor.setRawValue(obj.get("value"))
            }

            if (obj.hasProperty("get")) {
                val getter = obj.get("get")
                if (!Operations.isCallable(getter) && getter != JSUndefined)
                    throwTypeError("descriptor's 'get' property must be undefined or callable")
                descriptor.getter = getter
            }

            if (obj.hasProperty("set")) {
                val setter = obj.get("set")
                if (!Operations.isCallable(setter) && setter != JSUndefined)
                    throwTypeError("descriptor's 'set' property must be undefined or callable")
                descriptor.setter = setter
            }

            if (descriptor.hasGetter || descriptor.hasSetter) {
                if (descriptor.value != JSEmpty || descriptor.hasWritable)
                    throwTypeError("descriptor cannot specify 'get' or 'set' property with a 'value' or 'writable' property")
            }

            return descriptor
        }
    }
}
