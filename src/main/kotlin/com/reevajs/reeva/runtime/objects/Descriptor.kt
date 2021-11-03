package com.reevajs.reeva.runtime.objects

import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.primitives.JSAccessor
import com.reevajs.reeva.runtime.primitives.JSEmpty
import com.reevajs.reeva.runtime.primitives.JSNativeProperty
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.expect
import com.reevajs.reeva.utils.toValue

data class Descriptor constructor(
    private var value: JSValue,
    var attributes: Int,
) {
    @ECMAImpl("6.2.5.1")
    val isAccessorDescriptor: Boolean
        get() = value is JSAccessor

    @ECMAImpl("6.2.5.2")
    val isDataDescriptor: Boolean
        get() = (!value.isEmpty && !isAccessorDescriptor) || hasWritable

    @ECMAImpl("6.2.5.3")
    val isGenericDescriptor: Boolean
        get() = !isAccessorDescriptor && !isDataDescriptor

    val isEmpty: Boolean
        get() = value == JSUndefined && attributes == 0 && !hasGetterFunction && !hasSetterFunction

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
        get() = value.let { it is JSAccessor && it.getter != null }

    val hasSetterFunction: Boolean
        get() = value.let { it is JSAccessor && it.setter != null }

    val isConfigurable: Boolean
        get() = attributes and CONFIGURABLE != 0

    val isEnumerable: Boolean
        get() = attributes and ENUMERABLE != 0

    val isWritable: Boolean
        get() = attributes and WRITABLE != 0

    var getter: JSFunction?
        get() {
            expect(isAccessorDescriptor)
            return (value as JSAccessor).getter
        }
        set(newGetter) {
            expect(isAccessorDescriptor)
            (value as JSAccessor).getter = newGetter
        }

    var setter: JSFunction?
        get() {
            expect(isAccessorDescriptor)
            return (value as JSAccessor).setter
        }
        set(newSetter) {
            expect(isAccessorDescriptor)
            (value as JSAccessor).setter = newSetter
        }

    init {
        if (attributes and CONFIGURABLE != 0)
            attributes = attributes or HAS_CONFIGURABLE
        if (attributes and ENUMERABLE != 0)
            attributes = attributes or HAS_ENUMERABLE
        if (attributes and WRITABLE != 0)
            attributes = attributes or HAS_WRITABLE
        if (value.let { it is JSAccessor && it.getter != null })
            attributes = attributes or HAS_GETTER
        if (value.let { it is JSAccessor && it.setter != null })
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

    fun getActualValue(realm: Realm, thisValue: JSValue?): JSValue {
        return when (val v = value) {
            is JSNativeProperty -> v.get(realm, thisValue!!)
            is JSAccessor -> v.callGetter(thisValue!!)
            else -> v.ifEmpty(JSUndefined)
        }
    }

    fun setActualValue(realm: Realm, thisValue: JSValue?, newValue: JSValue) {
        when (val v = value) {
            is JSNativeProperty -> v.set(realm, thisValue!!, newValue)
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
            obj.set("get", (value as JSAccessor).getter ?: JSUndefined)
            obj.set("set", (value as JSAccessor).setter ?: JSUndefined)
        } else if (isDataDescriptor) {
            obj.set("value", getActualValue(realm, thisValue))
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
        fun fromObject(realm: Realm, obj: JSValue): Descriptor {
            if (obj !is JSObject)
                Errors.TODO("fromObject").throwTypeError(realm)

            val descriptor = Descriptor(JSEmpty, 0)
            if (obj.hasProperty("enumerable")) {
                descriptor.setHasEnumerable()
                descriptor.setEnumerable(Operations.toBoolean(obj.get("enumerable")))
            }

            if (obj.hasProperty("configurable")) {
                descriptor.setHasConfigurable()
                descriptor.setConfigurable(Operations.toBoolean(obj.get("configurable")))
            }

            if (obj.hasProperty("writable")) {
                descriptor.setHasWritable()
                descriptor.setWritable(Operations.toBoolean(obj.get("writable")))
            }

            if (obj.hasProperty("value")) {
                descriptor.setRawValue(obj.get("value"))
            }

            var getter: JSFunction? = null
            var setter: JSFunction? = null
            var hasGetterOrSetter = false

            if (obj.hasProperty("get")) {
                val getterTemp = obj.get("get")
                if (!Operations.isCallable(getterTemp) && getterTemp != JSUndefined)
                    Errors.DescriptorGetType.throwTypeError(realm)
                getter = getterTemp as? JSFunction
                descriptor.attributes = descriptor.attributes or HAS_GETTER
                hasGetterOrSetter = true
            }

            if (obj.hasProperty("set")) {
                val setterTemp = obj.get("set")
                if (!Operations.isCallable(setterTemp) && setterTemp != JSUndefined)
                    Errors.DescriptorSetType.throwTypeError(realm)
                setter = setterTemp as? JSFunction
                descriptor.attributes = descriptor.attributes or HAS_SETTER
                hasGetterOrSetter = true
            }

            if (hasGetterOrSetter) {
                if (descriptor.value != JSEmpty || descriptor.hasWritable)
                    Errors.DescriptorPropType.throwTypeError(realm)
                descriptor.setRawValue(JSAccessor(getter, setter))
            }

            return descriptor
        }
    }
}
