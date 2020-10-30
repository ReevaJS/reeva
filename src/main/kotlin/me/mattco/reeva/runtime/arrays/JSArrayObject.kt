package me.mattco.reeva.runtime.arrays

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSNativePropertyGetter
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.errors.JSRangeErrorObject
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.PropertyKey
import me.mattco.reeva.runtime.primitives.JSString
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.*

open class JSArrayObject protected constructor(realm: Realm, proto: JSValue = realm.arrayProto) : JSObject(realm, proto) {
    @JSNativePropertyGetter("length", Descriptor.WRITABLE)
    fun getLength(thisValue: JSValue): JSValue {
        expect(thisValue is JSObject)
        return thisValue.indexedProperties.arrayLikeSize.toValue()
    }

    @ECMAImpl("9.4.2.1", "[[DefineOwnProperty]]")
    @ECMAImpl("9.4.2.4", "ArrayLengthSet")
    override fun defineOwnProperty(property: PropertyKey, descriptor: Descriptor): Boolean {
        if (property.isString && property.asString == "length") {
            val value = descriptor.getActualValue(this)
            if (value == JSUndefined)
                return super.defineOwnProperty(property, descriptor)
            val newLenDesc = descriptor.copy()
            val newLenObj = Operations.toUint32(value)
            val numberLen = Operations.toNumeric(value)
            if (!newLenObj.sameValue(numberLen))
                throwRangeError("invalid array length: ${Operations.toPrintableString(value)}")

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

            val succeeded = super.defineOwnProperty(property, newLenDesc)
            if (!succeeded)
                return false
            indexedProperties.indices().filter {
                it >= newLen
            }.forEach {
                val deleteSucceeded = delete(it)
                if (!deleteSucceeded) {
                    newLenDesc.setActualValue(this, (it + 1).toValue())
                    if (!newWritable)
                        newLenDesc.setWritable(false)
                    super.defineOwnProperty(property, newLenDesc)
                    return false
                }
            }

            if (!newWritable)
                ecmaAssert(super.defineOwnProperty(property, Descriptor(JSUndefined, Descriptor.HAS_WRITABLE)))

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
        fun create(realm: Realm, proto: JSObject? = null) = JSArrayObject(realm, proto ?: realm.arrayProto).also { it.init() }
    }
}
