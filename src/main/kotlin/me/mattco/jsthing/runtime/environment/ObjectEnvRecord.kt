package me.mattco.jsthing.runtime.environment

import me.mattco.jsthing.runtime.annotations.ECMAImpl
import me.mattco.jsthing.runtime.values.JSValue
import me.mattco.jsthing.runtime.values.nonprimitives.objects.JSObject

class ObjectEnvRecord(
    val boundObject: JSObject,
    outerEnv: EnvRecord? = null
) : EnvRecord(outerEnv) {
    @ECMAImpl("HasBinding", "8.1.1.2.1")
    override fun hasBinding(name: String): Boolean {
        TODO("Not yet implemented")
    }

    @ECMAImpl("CreateMutableBinding", "8.1.1.2.2")
    override fun createMutableBinding(name: String, canBeDeleted: Boolean) {
        TODO("Not yet implemented")
    }

    @ECMAImpl("CreateImmutableBinding", "8.1.1.2.3")
    override fun createImmutableBinding(name: String, throwOnRepeatInitialization: Boolean) {
        TODO("Not yet implemented")
    }

    @ECMAImpl("InitializeBinding", "8.1.1.2.4")
    override fun initializeBinding(name: String, value: JSValue) {
        TODO("Not yet implemented")
    }

    @ECMAImpl("SetMutableBinding", "8.1.1.2.5")
    override fun setMutableBinding(name: String, value: JSValue, throwOnFailure: Boolean) {
        TODO("Not yet implemented")
    }

    @ECMAImpl("GetBindingValue", "8.1.1.2.6")
    override fun getBindingValue(name: String, throwOnNotFound: Boolean): JSValue {
        TODO("Not yet implemented")
    }

    @ECMAImpl("DeleteBinding", "8.1.1.2.7")
    override fun deleteBinding(name: String): Boolean {
        TODO("Not yet implemented")
    }

    @ECMAImpl("HasThisBinding", "8.1.1.2.8")
    override fun hasThisBinding(): Boolean {
        TODO("Not yet implemented")
    }

    @ECMAImpl("HasSuperBinding", "8.1.1.2.9")
    override fun hasSuperBinding(): Boolean {
        TODO("Not yet implemented")
    }

    @ECMAImpl("WithBaseObject", "8.1.1.2.10")
    override fun withBaseObject(): JSValue {
        TODO("Not yet implemented")
    }
}
