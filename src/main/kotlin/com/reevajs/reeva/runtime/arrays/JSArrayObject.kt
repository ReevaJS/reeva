package com.reevajs.reeva.runtime.arrays

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.PropertyKey
import com.reevajs.reeva.runtime.primitives.JSEmpty
import com.reevajs.reeva.runtime.primitives.JSString
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.*

open class JSArrayObject protected constructor(
    realm: Realm,
    proto: JSValue = realm.arrayProto,
) : JSObject(realm, proto) {
    override fun init() {
        super.init()

        defineNativeProperty("length", attrs { -conf; -enum; +writ }, ::getLength, ::setLength)
    }

    @ECMAImpl("9.4.2.1", "[[DefineOwnProperty]]")
    @ECMAImpl("9.4.2.4", "ArraySetLength")
    override fun defineOwnProperty(property: PropertyKey, descriptor: Descriptor): Boolean {
        if (property.isString && property.asString == "length") {
            if (descriptor.getRawValue() == JSEmpty)
                return super.defineOwnProperty(property, descriptor)
            val value = descriptor.getActualValue(this)
            val newLenDesc = descriptor.copy()
            val newLenObj = value.toUint32()
            val numberLen = value.toNumeric()
            if (!newLenObj.sameValue(numberLen))
                Errors.InvalidArrayLength(value.toString()).throwRangeError()

            val newLen = newLenObj.asLong
            newLenDesc.setActualValue(this, newLenObj)
            val oldLenDesc = getOwnPropertyDescriptor("length")
            expect(oldLenDesc != null)
            ecmaAssert(oldLenDesc.isDataDescriptor)
            ecmaAssert(!oldLenDesc.isConfigurable)

            val oldLen = oldLenDesc.getActualValue(this).asLong
            if (newLen >= oldLen) {
                indexedProperties.setArrayLikeSize(newLen)
                return super.defineOwnProperty(property, newLenDesc)
            }
            if (!oldLenDesc.isWritable)
                return false
            val newWritable = if (!newLenDesc.hasWritable || newLenDesc.isWritable) {
                true
            } else {
                newLenDesc.setWritable(true)
                false
            }

            // The spec specifies that the elements should be deleted _after_ the length property is set, however
            // unfortunately this is not possible here. If we set the length property, IndexedStorage will return
            // null for any descriptor whose index is above the length we just set.
            indexedProperties.indices().filter {
                it >= newLen
            }.forEach {
                val deleteSucceeded = delete(it)
                if (!deleteSucceeded) {
                    newLenDesc.setActualValue(this, (it + 1L).toValue())
                    if (!newWritable)
                        newLenDesc.setWritable(false)
                    super.defineOwnProperty(property, newLenDesc)
                    return false
                }
            }
            indexedProperties.setArrayLikeSize(newLen)
            val succeeded = super.defineOwnProperty(property, newLenDesc)
            if (!succeeded)
                return false

            if (!newWritable)
                ecmaAssert(super.defineOwnProperty(property, Descriptor(JSEmpty, Descriptor.HAS_WRITABLE)))

            return true
        }

        val arrayIndex = when {
            property.isInt -> property.asInt.toLong()
            property.isLong -> property.asLong
            else -> null
        }

        if (arrayIndex != null) {
            val oldLenDesc = getOwnPropertyDescriptor("length")
            expect(oldLenDesc != null)
            ecmaAssert(oldLenDesc.isDataDescriptor)
            ecmaAssert(!oldLenDesc.isConfigurable)

            val oldLen = oldLenDesc.getActualValue(this).let {
                if (it is JSString) it.string.toInt() else it.asInt
            }
            ecmaAssert(oldLen >= 0)

            val index = arrayIndex % AOs.MAX_32BIT_INT
            if (index >= oldLen && !oldLenDesc.isWritable)
                return false

            if (!super.defineOwnProperty(property, descriptor))
                return false
            if (index >= oldLen) {
                oldLenDesc.setActualValue(this, (index + 1).toValue())
                ecmaAssert(super.defineOwnProperty("length".key(), oldLenDesc))
            }
            return true
        }

        return super.defineOwnProperty(property, descriptor)
    }

    companion object {
        fun create(
            realm: Realm = Agent.activeAgent.getActiveRealm(),
            proto: JSValue = realm.arrayProto,
        ) = JSArrayObject(realm, proto).initialize()

        fun getLength(thisValue: JSValue): JSValue {
            expect(thisValue is JSObject)
            return thisValue.indexedProperties.arrayLikeSize.toValue()
        }

        fun setLength(thisValue: JSValue, newLength: JSValue): JSValue {
            expect(thisValue is JSObject)
            thisValue.indexedProperties.setArrayLikeSize(newLength.toLength().asLong)
            return JSUndefined
        }
    }
}
