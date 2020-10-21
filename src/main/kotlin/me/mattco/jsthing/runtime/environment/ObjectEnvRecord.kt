package me.mattco.jsthing.runtime.environment

import me.mattco.jsthing.runtime.annotations.ECMAImpl
import me.mattco.jsthing.runtime.values.JSValue
import me.mattco.jsthing.runtime.values.nonprimitives.objects.Attributes
import me.mattco.jsthing.runtime.values.nonprimitives.objects.Attributes.Companion.CONFIGURABLE
import me.mattco.jsthing.runtime.values.nonprimitives.objects.Attributes.Companion.ENUMERABLE
import me.mattco.jsthing.runtime.values.nonprimitives.objects.Attributes.Companion.WRITABLE
import me.mattco.jsthing.runtime.values.nonprimitives.objects.Descriptor
import me.mattco.jsthing.runtime.values.nonprimitives.objects.JSObject
import me.mattco.jsthing.runtime.values.primitives.JSUndefined
import me.mattco.jsthing.utils.shouldThrowError
import me.mattco.jsthing.utils.unreachable

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

    @ECMAImpl("CreateMutableBinding", "8.1.1.2.2")
    override fun createMutableBinding(name: String, canBeDeleted: Boolean) {
        val descriptor = Descriptor(JSUndefined, Attributes(ENUMERABLE and WRITABLE and if (canBeDeleted) CONFIGURABLE else 0))
        if (!boundObject.defineOwnProperty(name, descriptor))
            shouldThrowError("TypeError")
    }

    @ECMAImpl("CreateImmutableBinding", "8.1.1.2.3")
    override fun createImmutableBinding(name: String, throwOnRepeatInitialization: Boolean) {
        unreachable()
    }

    @ECMAImpl("InitializeBinding", "8.1.1.2.4")
    override fun initializeBinding(name: String, value: JSValue) {
        setMutableBinding(name, value, false)
    }

    @ECMAImpl("SetMutableBinding", "8.1.1.2.5")
    override fun setMutableBinding(name: String, value: JSValue, throwOnFailure: Boolean) {
        if (!boundObject.hasProperty(name) && throwOnFailure)
            shouldThrowError("ReferenceError")
        if (!boundObject.set(name, value) && throwOnFailure)
            shouldThrowError("TypeError")
    }

    @ECMAImpl("GetBindingValue", "8.1.1.2.6")
    override fun getBindingValue(name: String, throwOnNotFound: Boolean): JSValue {
        if (!boundObject.hasProperty(name)) {
            if (throwOnNotFound)
                shouldThrowError("ReferenceError")
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
