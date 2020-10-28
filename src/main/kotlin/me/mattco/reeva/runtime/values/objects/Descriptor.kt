package me.mattco.reeva.runtime.values.objects

import me.mattco.reeva.runtime.Agent.Companion.checkError
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.values.functions.JSFunction
import me.mattco.reeva.runtime.values.primitives.*
import me.mattco.reeva.utils.throwError
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

        obj.set("configurable", isConfigurable.toValue())
        obj.set("enumerable", isEnumerable.toValue())
        if (isDataDescriptor)
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

        @JSThrows
        @ECMAImpl("ToPropertyDescriptor", "6.2.5.5")
        fun fromObject(obj: JSValue): Descriptor {
            val descriptor = Descriptor(JSEmpty, 0)
            if (obj !is JSObject) {
                throwError<JSTypeErrorObject>("TODO: message")
                return descriptor
            }
            if (obj.hasProperty("enumerable")) {
                descriptor.setHasEnumerable()
                descriptor.setEnumerable(Operations.toBoolean(obj.get("enumerable")) == JSTrue)
                checkError() ?: return descriptor
            }

            if (obj.hasProperty("configurable")) {
                descriptor.setHasConfigurable()
                descriptor.setConfigurable(Operations.toBoolean(obj.get("configurable")) == JSTrue)
                checkError() ?: return descriptor
            }

            if (obj.hasProperty("writable")) {
                descriptor.setHasWritable()
                descriptor.setWritable(Operations.toBoolean(obj.get("writable")) == JSTrue)
                checkError() ?: return descriptor
            }

            if (obj.hasProperty("value")) {
                descriptor.setRawValue(obj.get("value"))
                checkError() ?: return descriptor
            }

            if (obj.hasProperty("get")) {
                val getter = obj.get("get")
                checkError() ?: return descriptor
                if (!Operations.isCallable(getter) && getter != JSUndefined) {
                    throwError<JSTypeErrorObject>("descriptor's 'get' property must be undefined or callable")
                    return descriptor
                }
            }

            if (obj.hasProperty("set")) {
                val setter = obj.get("set")
                checkError() ?: return descriptor
                if (!Operations.isCallable(setter) && setter != JSUndefined) {
                    throwError<JSTypeErrorObject>("descriptor's 'set' property must be undefined or callable")
                    return descriptor
                }
            }

            if (descriptor.getter != null || descriptor.setter != null) {
                if (descriptor.value != JSEmpty || descriptor.hasWritable) {
                    throwError<JSTypeErrorObject>("descriptor cannot specify 'get' or 'set' property with a 'value' or 'writable' property")
                    return descriptor
                }
            }

            return descriptor
        }
    }
}
