package me.mattco.reeva.runtime.arrays

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSNativePropertyGetter
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.annotations.JSNativePropertySetter
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.PropertyKey
import me.mattco.reeva.runtime.primitives.JSEmpty
import me.mattco.reeva.runtime.primitives.JSString
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.*

open class JSArrayObject protected constructor(realm: Realm, proto: JSValue = realm.arrayProto) : JSObject(realm, proto) {
    @JSNativePropertyGetter("length", "ceW")
    fun getLength(thisValue: JSValue): JSValue {
        expect(thisValue is JSObject)
        return thisValue.indexedProperties.arrayLikeSize.toValue()
    }

    @JSNativePropertySetter("length", "ceW")
    fun setLength(thisValue: JSValue, newLength: JSValue): JSValue {
        expect(thisValue is JSObject)
        thisValue.indexedProperties.setArrayLikeSize(Operations.toLength(newLength).asLong)
        return JSUndefined
    }

    @ECMAImpl("9.4.2.1", "[[DefineOwnProperty]]")
    @ECMAImpl("9.4.2.4", "ArraySetLength")
    override fun defineOwnProperty(property: PropertyKey, descriptor: Descriptor): Boolean {
        if (property.isString && property.asString == "length") {
            if (descriptor.getRawValue() == JSEmpty)
                return super.defineOwnProperty(property, descriptor)
            val value = descriptor.getActualValue(this)
            val newLenDesc = descriptor.copy()
            val newLenObj = Operations.toUint32(value)
            val numberLen = Operations.toNumeric(value)
            if (!newLenObj.sameValue(numberLen))
                Errors.InvalidArrayLength(Operations.toPrintableString(value)).throwRangeError()

            val newLen = newLenObj.asInt
            newLenDesc.setActualValue(this, newLenObj)
            val oldLenDesc = getOwnPropertyDescriptor("length")
            expect(oldLenDesc != null)
            ecmaAssert(oldLenDesc.isDataDescriptor)
            ecmaAssert(!oldLenDesc.isConfigurable)

            val oldLen = oldLenDesc.getActualValue(this).asInt
            if (newLen >= oldLen)
                return super.defineOwnProperty(property, newLenDesc)
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
            val succeeded = super.defineOwnProperty(property, newLenDesc)
            if (!succeeded)
                return false

            if (!newWritable)
                ecmaAssert(super.defineOwnProperty(property, Descriptor(JSEmpty, Descriptor.HAS_WRITABLE)))

            return true
        }

        if (property.isInt && property.asInt >= 0 || (property.isString && property.asString.toIntOrNull() != null)) {
            val oldLenDesc = getOwnPropertyDescriptor("length")
            expect(oldLenDesc != null)
            ecmaAssert(oldLenDesc.isDataDescriptor)
            ecmaAssert(!oldLenDesc.isConfigurable)

            val oldLen = oldLenDesc.getActualValue(this).let {
                if (it is JSString) it.string.toInt() else it.asInt
            }
            ecmaAssert(oldLen >= 0)

            val index = Operations.toUint32(property.asValue).asInt
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
        fun create(realm: Realm, proto: JSValue = realm.arrayProto) = JSArrayObject(realm, proto).initialize()
    }
}
