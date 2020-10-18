package me.mattco.jsthing.runtime.environment

import me.mattco.jsthing.runtime.values.JSValue
import me.mattco.jsthing.runtime.values.nonprimitives.objects.JSObject

class ObjectEnvRecord(
    val boundObject: JSObject,
    outerEnv: EnvRecord? = null
) : EnvRecord(outerEnv) {
    override fun hasBinding(name: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun createMutableBinding(name: String, canBeDeleted: Boolean) {
        TODO("Not yet implemented")
    }

    override fun createImmutableBinding(name: String, throwOnRepeatInitialization: Boolean) {
        TODO("Not yet implemented")
    }

    override fun initializeBinding(name: String, value: JSValue) {
        TODO("Not yet implemented")
    }

    override fun setMutableBinding(name: String, value: JSValue, throwOnFailure: Boolean) {
        TODO("Not yet implemented")
    }

    override fun getBindingValue(name: String, throwOnNotFound: Boolean): JSValue {
        TODO("Not yet implemented")
    }

    override fun deleteBinding(name: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun hasThisBinding(): Boolean {
        TODO("Not yet implemented")
    }

    override fun hasSuperBinding(): Boolean {
        TODO("Not yet implemented")
    }

    override fun withBaseObject(): JSValue {
        TODO("Not yet implemented")
    }
}
