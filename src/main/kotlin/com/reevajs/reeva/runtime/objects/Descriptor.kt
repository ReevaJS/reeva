package com.reevajs.reeva.runtime.objects

import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.primitives.JSAccessor
import com.reevajs.reeva.runtime.primitives.JSEmpty
import com.reevajs.reeva.runtime.primitives.JSNativeProperty
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.toBoolean
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.expect
import com.reevajs.reeva.utils.toValue

data class Descriptor constructor(
    private var valueBacker: JSValue,
    var attributes: Int = 0,
) {
    // To enforce a private setter
    val value: JSValue get() = valueBacker

    @ECMAImpl("6.2.5.1")
    val isAccessorDescriptor: Boolean
        get() = valueBacker is JSAccessor

    @ECMAImpl("6.2.5.2")
    val isDataDescriptor: Boolean
        get() = (!valueBacker.isEmpty && !isAccessorDescriptor) || hasWritable

    @ECMAImpl("6.2.5.3")
    val isGenericDescriptor: Boolean
        get() = !isAccessorDescriptor && !isDataDescriptor

    val isEmpty: Boolean
        get() = valueBacker == JSUndefined && attributes == 0 && !hasGetterFunction && !hasSetterFunction

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

    val hasGetterFunction: Boolean
        get() = valueBacker.let { it is JSAccessor && it.getter != null }

    val hasSetterFunction: Boolean
        get() = valueBacker.let { it is JSAccessor && it.setter != null }

    val isConfigurable: Boolean
        get() = attributes and CONFIGURABLE != 0

    val isEnumerable: Boolean
        get() = attributes and ENUMERABLE != 0

    val isWritable: Boolean
        get() = attributes and WRITABLE != 0

    var getter: JSFunction?
        get() {
            expect(isAccessorDescriptor)
            return (valueBacker as JSAccessor).getter
        }
        set(newGetter) {
            expect(isAccessorDescriptor)
            (valueBacker as JSAccessor).getter = newGetter
        }

    var setter: JSFunction?
        get() {
            expect(isAccessorDescriptor)
            return (valueBacker as JSAccessor).setter
        }
        set(newSetter) {
            expect(isAccessorDescriptor)
            (valueBacker as JSAccessor).setter = newSetter
        }

    init {
        if (attributes and CONFIGURABLE != 0)
            attributes = attributes or HAS_CONFIGURABLE
        if (attributes and ENUMERABLE != 0)
            attributes = attributes or HAS_ENUMERABLE
        if (attributes and WRITABLE != 0)
            attributes = attributes or HAS_WRITABLE
        if (valueBacker.let { it is JSAccessor && it.getter != null })
            attributes = attributes or HAS_GETTER
        if (valueBacker.let { it is JSAccessor && it.setter != null })
            attributes = attributes or HAS_SETTER
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

    fun setConfigurable(configurable: Boolean = true) = apply {
        attributes = if (configurable) {
            attributes or CONFIGURABLE
        } else {
            attributes and CONFIGURABLE.inv()
        } or HAS_CONFIGURABLE
    }

    fun setEnumerable(enumerable: Boolean = true) = apply {
        attributes = if (enumerable) {
            attributes or ENUMERABLE
        } else {
            attributes and ENUMERABLE.inv()
        } or HAS_ENUMERABLE
    }

    fun setWritable(writable: Boolean = true) = apply {
        attributes = if (writable) {
            attributes or WRITABLE
        } else {
            attributes and WRITABLE.inv()
        } or HAS_WRITABLE
    }

    fun getActualValue(thisValue: JSValue?): JSValue {
        return when (val v = valueBacker) {
            is JSNativeProperty -> v.get(thisValue!!)
            is JSAccessor -> v.callGetter(thisValue!!)
            else -> v.ifEmpty(JSUndefined)
        }
    }

    fun setActualValue(thisValue: JSValue?, newValue: JSValue) {
        when (val v = valueBacker) {
            is JSNativeProperty -> v.set(thisValue!!, newValue)
            is JSAccessor -> v.callSetter(thisValue!!, newValue)
            else -> valueBacker = newValue
        }
    }

    fun getRawValue(): JSValue = valueBacker

    fun setRawValue(value: JSValue) {
        this.valueBacker = value
    }

    @ECMAImpl("6.2.5.4", "FromPropertyDescriptor")
    fun toObject(thisValue: JSValue): JSObject {
        val obj = JSObject.create()
        if (isAccessorDescriptor) {
            obj.set("get", (valueBacker as JSAccessor).getter ?: JSUndefined)
            obj.set("set", (valueBacker as JSAccessor).setter ?: JSUndefined)
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

    @ECMAImpl("6.2.5.6")
    fun complete() = apply {
        if (isGenericDescriptor) {
            if (valueBacker == JSEmpty)
                valueBacker = JSUndefined
            if (!hasWritable)
                attributes = attributes or HAS_WRITABLE
        }

        if (!hasConfigurable)
            attributes = attributes or HAS_CONFIGURABLE
        if (!hasEnumerable)
            attributes = attributes or HAS_ENUMERABLE
    }

    override fun toString() = buildString {
        append("Descriptor(")
        when {
            isAccessorDescriptor -> append("type=accessor")
            isDataDescriptor -> append("type=data")
            else -> append("type=generic")
        }

        if (attributes != 0) {
            append(", attributes=")
            if (hasConfigurable)
                append(if (isConfigurable) 'C' else 'c')
            if (hasEnumerable)
                append(if (isEnumerable) 'E' else 'e')
            if (isDataDescriptor) {
                if (hasWritable)
                    append(if (isWritable) 'W' else 'w')
            } else if (isAccessorDescriptor) {
                append(if (hasGetter) 'G' else 'g')
                append(if (hasSetter) 'S' else 's')
            }
        }

        append(')')
    }

    companion object {
        const val CONFIGURABLE = 1 shl 0
        const val ENUMERABLE = 1 shl 1
        const val WRITABLE = 1 shl 2
        const val HAS_CONFIGURABLE = 1 shl 3
        const val HAS_ENUMERABLE = 1 shl 4
        const val HAS_WRITABLE = 1 shl 5
        const val HAS_GETTER = 1 shl 6
        const val HAS_SETTER = 1 shl 7
        const val HAS_BASIC = HAS_CONFIGURABLE or HAS_ENUMERABLE or HAS_WRITABLE

        const val DEFAULT_ATTRIBUTES = CONFIGURABLE or ENUMERABLE or WRITABLE or HAS_BASIC

        @ECMAImpl("6.2.5.5", "ToPropertyDescriptor")
        fun fromObject(obj: JSValue): Descriptor {
            if (obj !is JSObject)
                Errors.TODO("fromObject").throwTypeError()

            val descriptor = Descriptor(JSEmpty, 0)
            if (obj.hasProperty("enumerable")) {
                descriptor.setHasEnumerable()
                descriptor.setEnumerable(obj.get("enumerable").toBoolean())
            }

            if (obj.hasProperty("configurable")) {
                descriptor.setHasConfigurable()
                descriptor.setConfigurable(obj.get("configurable").toBoolean())
            }

            if (obj.hasProperty("writable")) {
                descriptor.setHasWritable()
                descriptor.setWritable(obj.get("writable").toBoolean())
            }

            if (obj.hasProperty("value")) {
                descriptor.setRawValue(obj.get("value"))
            }

            var getter: JSFunction? = null
            var setter: JSFunction? = null
            var hasGetterOrSetter = false

            if (obj.hasProperty("get")) {
                val getterTemp = obj.get("get")
                if (!AOs.isCallable(getterTemp) && getterTemp != JSUndefined)
                    Errors.DescriptorGetType.throwTypeError()
                getter = getterTemp as? JSFunction
                descriptor.attributes = descriptor.attributes or HAS_GETTER
                hasGetterOrSetter = true
            }

            if (obj.hasProperty("set")) {
                val setterTemp = obj.get("set")
                if (!AOs.isCallable(setterTemp) && setterTemp != JSUndefined)
                    Errors.DescriptorSetType.throwTypeError()
                setter = setterTemp as? JSFunction
                descriptor.attributes = descriptor.attributes or HAS_SETTER
                hasGetterOrSetter = true
            }

            if (hasGetterOrSetter) {
                if (descriptor.valueBacker != JSEmpty || descriptor.hasWritable)
                    Errors.DescriptorPropType.throwTypeError()
                descriptor.setRawValue(JSAccessor(getter, setter))
            }

            return descriptor
        }
    }
}
