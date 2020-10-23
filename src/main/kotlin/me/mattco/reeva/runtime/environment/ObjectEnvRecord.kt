package me.mattco.reeva.runtime.environment

import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.errors.JSReferenceErrorObject
import me.mattco.reeva.runtime.values.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.values.objects.Attributes
import me.mattco.reeva.runtime.values.objects.Attributes.Companion.CONFIGURABLE
import me.mattco.reeva.runtime.values.objects.Attributes.Companion.ENUMERABLE
import me.mattco.reeva.runtime.values.objects.Attributes.Companion.WRITABLE
import me.mattco.reeva.runtime.values.objects.Descriptor
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.primitives.JSUndefined
import me.mattco.reeva.utils.shouldThrowError
import me.mattco.reeva.utils.throwError
import me.mattco.reeva.utils.unreachable

class ObjectEnvRecord(
    val boundObject: JSObject,
    outerEnv: EnvRecord? = null,
    var withEnvironment: Boolean = false,
) : EnvRecord(outerEnv) {
    @ECMAImpl("HasBinding", "8.1.1.2.1")
    override fun hasBinding(name: String): Boolean {
        if (!boundObject.hasProperty(name))
            return false
        if (!withEnvironment)
            return true
        TODO()
    }

    @JSThrows
    @ECMAImpl("CreateMutableBinding", "8.1.1.2.2")
    override fun createMutableBinding(name: String, canBeDeleted: Boolean) {
        val descriptor = Descriptor(JSUndefined, Attributes(ENUMERABLE and WRITABLE and if (canBeDeleted) CONFIGURABLE else 0))
        if (!boundObject.defineOwnProperty(name, descriptor))
            throwError<JSTypeErrorObject>("TODO")
    }

    @ECMAImpl("CreateImmutableBinding", "8.1.1.2.3")
    override fun createImmutableBinding(name: String, throwOnRepeatInitialization: Boolean) {
        unreachable()
    }

    @JSThrows
    @ECMAImpl("InitializeBinding", "8.1.1.2.4")
    override fun initializeBinding(name: String, value: JSValue) {
        setMutableBinding(name, value, false)
    }

    @JSThrows
    @ECMAImpl("SetMutableBinding", "8.1.1.2.5")
    override fun setMutableBinding(name: String, value: JSValue, throwOnFailure: Boolean) {
        if (!boundObject.hasProperty(name) && throwOnFailure) {
            throwError<JSReferenceErrorObject>("TODO")
            return
        }
        if (!boundObject.set(name, value) && throwOnFailure)
            throwError<JSTypeErrorObject>("TODO")
    }

    @JSThrows
    @ECMAImpl("GetBindingValue", "8.1.1.2.6")
    override fun getBindingValue(name: String, throwOnNotFound: Boolean): JSValue {
        if (!boundObject.hasProperty(name)) {
            if (throwOnNotFound)
                throwError<JSReferenceErrorObject>("TODO")
            return JSUndefined
        }
        return boundObject.get(name)
    }

    @ECMAImpl("DeleteBinding", "8.1.1.2.7")
    override fun deleteBinding(name: String): Boolean {
        return boundObject.delete(name)
    }

    @ECMAImpl("HasThisBinding", "8.1.1.2.8")
    override fun hasThisBinding() = false

    @ECMAImpl("HasSuperBinding", "8.1.1.2.9")
    override fun hasSuperBinding() = false

    @ECMAImpl("WithBaseObject", "8.1.1.2.10")
    override fun withBaseObject() = if (withEnvironment) boundObject else JSUndefined
}
