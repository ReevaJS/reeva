package me.mattco.reeva.runtime.builtins

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.SlotName
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.PropertyKey
import me.mattco.reeva.runtime.primitives.JSEmpty
import me.mattco.reeva.utils.ecmaAssert

class JSMappedArgumentsObject private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    var parameterMap by lateinitSlot<JSObject>(SlotName.ParameterMap)

    override fun getOwnPropertyDescriptor(property: PropertyKey): Descriptor? {
        val desc = super.getOwnPropertyDescriptor(property) ?: return null
        if (Operations.hasOwnProperty(parameterMap, property)) {
            desc.setRawValue(parameterMap.get(property))
        }
        return desc
    }

    override fun defineOwnProperty(property: PropertyKey, descriptor: Descriptor): Boolean {
        val isMapped = Operations.hasOwnProperty(parameterMap, property)
        var newDescriptor = descriptor

        if (isMapped && descriptor.isDataDescriptor) {
            if (descriptor.getRawValue() == JSEmpty && descriptor.hasWritable && !descriptor.isWritable) {
                newDescriptor = descriptor.copy()
                newDescriptor.setRawValue(parameterMap.get(property))
            }
        }

        if (!super.defineOwnProperty(property, newDescriptor))
            return false

        if (isMapped) {
            if (descriptor.isAccessorDescriptor) {
                parameterMap.delete(property)
            } else {
                if (descriptor.getRawValue() != JSEmpty) {
                    val status = parameterMap.set(property, descriptor.getActualValue(realm, this))
                    ecmaAssert(status)
                }
                if (descriptor.hasWritable && !descriptor.isWritable)
                    parameterMap.delete(property)
            }
        }

        return true
    }

    override fun get(property: PropertyKey, receiver: JSValue): JSValue {
        if (!Operations.hasOwnProperty(parameterMap, property))
            return super.get(property, receiver)
        return parameterMap.get(property, parameterMap)
    }

    override fun set(property: PropertyKey, value: JSValue, receiver: JSValue): Boolean {
        val isMapped = if (!this.sameValue(receiver)) {
            false
        } else Operations.hasOwnProperty(parameterMap, property)

        if (isMapped) {
            val status = parameterMap.set(property, value)
            ecmaAssert(status)
        }

        return super.set(property, value, receiver)
    }

    override fun delete(property: PropertyKey): Boolean {
        val isMapped = Operations.hasOwnProperty(parameterMap, property)
        val result = super.delete(property)
        if (result && isMapped)
            parameterMap.delete(property)
        return result
    }

    companion object {
        fun create(realm: Realm) = JSMappedArgumentsObject(realm).initialize()
    }
}
