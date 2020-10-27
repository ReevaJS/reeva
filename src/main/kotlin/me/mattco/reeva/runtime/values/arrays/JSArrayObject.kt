package me.mattco.reeva.runtime.values.arrays

import me.mattco.reeva.runtime.Agent.Companion.checkError
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSNativePropertyGetter
import me.mattco.reeva.runtime.annotations.JSNativePropertySetter
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.errors.JSRangeErrorObject
import me.mattco.reeva.runtime.values.objects.Descriptor
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.objects.PropertyKey
import me.mattco.reeva.runtime.values.primitives.JSNumber
import me.mattco.reeva.runtime.values.primitives.JSString
import me.mattco.reeva.runtime.values.primitives.JSUndefined
import me.mattco.reeva.utils.*

open class JSArrayObject protected constructor(realm: Realm, proto: JSValue = realm.arrayProto) : JSObject(realm, proto) {
    @JSNativePropertyGetter("length", Descriptor.WRITABLE)
    fun getLength(thisValue: JSValue): JSValue {
        expect(thisValue is JSObject)
        return thisValue.indexedProperties.arrayLikeSize.toValue()
    }

    @JSNativePropertySetter("length", Descriptor.WRITABLE)
    fun setLength(thisValue: JSValue, argument: JSValue) {
        // TODO: This is not complete, and will also crash
        expect(thisValue is JSObject)
        thisValue.indexedProperties.setArrayLikeSize(argument.asInt)
    }

    @ECMAImpl("[[DefineOwnProperty]]", "9.4.2.1")
    @ECMAImpl("ArrayLengthSet", "9.4.2.4")
    override fun defineOwnProperty(property: PropertyKey, descriptor: Descriptor): Boolean {
        if (property.isString && property.asString == "length") {
            val value = descriptor.getActualValue(this)
            if (value == JSUndefined)
                return super.defineOwnProperty(property, descriptor)
            val newLenDesc = descriptor.copy()
            val newLenObj = Operations.toUint32(value)
            checkError() ?: return false
            val numberLen = Operations.toNumeric(value)
            checkError() ?: return false
            if (!newLenObj.sameValue(numberLen)) {
                throwError<JSRangeErrorObject>("invalid array length: ${Operations.toPrintableString(value)}")
                return false
            }
            val newLen = newLenObj.asInt
            newLenDesc.setActualValue(this, newLenObj)
            val oldLenDesc = getOwnPropertyDescriptor("length")
            checkError() ?: return false
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
            checkError() ?: return false
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
            if (!newWritable) {
                val succeeded = super.defineOwnProperty(property, Descriptor(JSUndefined, Descriptor.HAS_WRITABLE))
                checkError() ?: return false
                ecmaAssert(succeeded)
            }
            return true
        }

        if (property.isInt && property.asInt >= 0 || (property.isString && property.asString.toIntOrNull() != null)) {
            val oldLenDesc = getOwnPropertyDescriptor("length")
            checkError() ?: return false
            expect(oldLenDesc != null)
            ecmaAssert(oldLenDesc.isDataDescriptor)
            ecmaAssert(!oldLenDesc.isConfigurable)
            val oldLen = oldLenDesc.getActualValue(this).let {
                if (it is JSString) it.string.toInt() else it.asInt
            }
            ecmaAssert(oldLen >= 0)
            val index = Operations.toUint32(property.asValue).asInt
            checkError() ?: return false
            if (index >= oldLen && !oldLenDesc.isWritable)
                return false
            val succeeded = super.defineOwnProperty(property, descriptor)
            checkError() ?: return false
            if (!succeeded)
                return false
            if (index >= oldLen) {
                oldLenDesc.setActualValue(this, (index + 1).toValue())
                val succeeded = super.defineOwnProperty("length".key(), oldLenDesc)
                checkError() ?: return false
                ecmaAssert(succeeded)
            }
            return true
        }

        return super.defineOwnProperty(property, descriptor)
    }

    companion object {
        fun create(realm: Realm, proto: JSObject? = null) = JSArrayObject(realm, proto ?: realm.arrayProto).also { it.init() }
    }
}
