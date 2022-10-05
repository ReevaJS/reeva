package com.reevajs.reeva.runtime.collections

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.PropertyKey
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.JSEmpty
import com.reevajs.reeva.utils.ecmaAssert

class JSMappedArgumentsObject private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    var parameterMap by lateinitSlot(Slot.MappedParameterMap)

    override fun getOwnPropertyDescriptor(property: PropertyKey): Descriptor? {
        val desc = super.getOwnPropertyDescriptor(property) ?: return null
        if (AOs.hasOwnProperty(parameterMap, property)) {
            desc.setRawValue(parameterMap.get(property))
        }
        return desc
    }

    override fun defineOwnProperty(property: PropertyKey, descriptor: Descriptor): Boolean {
        val isMapped = AOs.hasOwnProperty(parameterMap, property)
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
                    val status = parameterMap.set(property, descriptor.getActualValue(this))
                    ecmaAssert(status)
                }
                if (descriptor.hasWritable && !descriptor.isWritable)
                    parameterMap.delete(property)
            }
        }

        return true
    }

    override fun get(property: PropertyKey, receiver: JSValue): JSValue {
        if (!AOs.hasOwnProperty(parameterMap, property))
            return super.get(property, receiver)
        return parameterMap.get(property, parameterMap)
    }

    override fun set(property: PropertyKey, value: JSValue, receiver: JSValue): Boolean {
        val isMapped = if (!this.sameValue(receiver)) {
            false
        } else AOs.hasOwnProperty(parameterMap, property)

        if (isMapped) {
            val status = parameterMap.set(property, value)
            ecmaAssert(status)
        }

        return super.set(property, value, receiver)
    }

    override fun delete(property: PropertyKey): Boolean {
        val isMapped = AOs.hasOwnProperty(parameterMap, property)
        val result = super.delete(property)
        if (result && isMapped)
            parameterMap.delete(property)
        return result
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSMappedArgumentsObject(realm).initialize()
    }
}
