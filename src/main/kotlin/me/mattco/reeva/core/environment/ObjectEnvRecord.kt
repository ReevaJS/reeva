package me.mattco.reeva.core.environment

import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.unreachable

class ObjectEnvRecord(
    val boundObject: JSObject,
    var withEnvironment: Boolean = false,
    outerEnv: EnvRecord? = null,
) : EnvRecord(outerEnv) {
    @ECMAImpl("8.1.1.2.1")
    override fun hasBinding(name: String): Boolean {
        if (!boundObject.hasProperty(name))
            return false
        if (!withEnvironment)
            return true
        TODO()
    }

    @ECMAImpl("8.1.1.2.2")
    override fun createMutableBinding(name: String, canBeDeleted: Boolean) {
        val attrs = Descriptor.ENUMERABLE or Descriptor.WRITABLE or if (canBeDeleted) Descriptor.CONFIGURABLE else 0
        if (!boundObject.defineOwnProperty(name, JSUndefined, attrs))
            Errors.TODO("ObjectEnvRecord createMutableBinding").throwTypeError()
    }

    @ECMAImpl("8.1.1.2.3")
    override fun createImmutableBinding(name: String, throwOnRepeatInitialization: Boolean) {
        unreachable()
    }

    @ECMAImpl("8.1.1.2.4")
    override fun initializeBinding(name: String, value: JSValue) {
        setMutableBinding(name, value, false)
    }

    @ECMAImpl("8.1.1.2.5")
    override fun setMutableBinding(name: String, value: JSValue, throwOnFailure: Boolean) {
        if (!boundObject.hasProperty(name) && throwOnFailure)
            Errors.TODO("ObjectEnvRecord setMutableBinding 1").throwReferenceError()
        if (!boundObject.set(name, value) && throwOnFailure)
            Errors.TODO("ObjectEnvRecord setMutableBinding 2").throwTypeError()
    }

    @ECMAImpl("8.1.1.2.6")
    override fun getBindingValue(name: String, throwOnNotFound: Boolean): JSValue {
        if (!boundObject.hasProperty(name)) {
            if (throwOnNotFound)
                Errors.TODO("ObjectEnvRecord getBindingValue").throwReferenceError()
            return JSUndefined
        }
        return boundObject.get(name)
    }

    @ECMAImpl("8.1.1.2.7")
    override fun deleteBinding(name: String): Boolean {
        return boundObject.delete(name)
    }

    @ECMAImpl("8.1.1.2.8")
    override fun hasThisBinding() = false

    @ECMAImpl("8.1.1.2.9")
    override fun hasSuperBinding() = false

    @ECMAImpl("8.1.1.2.10")
    override fun withBaseObject() = if (withEnvironment) boundObject else JSUndefined
}
