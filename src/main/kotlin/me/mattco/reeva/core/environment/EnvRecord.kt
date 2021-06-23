package me.mattco.reeva.core.environment

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSEmpty
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.key

/**
 * The runtime-equivalent of the Parser's Scope objects
 */
open class EnvRecord(val outer: EnvRecord?, val isStrict: Boolean, size: Int) {
    private val bindings: Array<JSValue> = Array(size) { JSEmpty }

    fun getBinding(index: Int): JSValue {
        val value = bindings[index]
        if (value == JSEmpty)
            TODO("throw access before init error")
        return value
    }

    fun setBinding(index: Int, value: JSValue) {
        expect(value != JSEmpty)
        bindings[index] = value
    }

    fun getExtensionBinding(name: String) = extension().get(name)

    fun setExtensionBinding(name: String, value: JSValue) = extension().set(name, value)

    open fun hasExtension() = false

    open fun extension(): JSObject = throw NotImplementedError()
}

class GlobalEnvRecord(private val realm: Realm, isStrict: Boolean, size: Int) : EnvRecord(null, isStrict, size) {
    override fun hasExtension() = true

    override fun extension() = realm.globalObject

    @ECMAImpl("9.1.1.4.14")
    fun hasRestrictedGlobalProperty(name: String): Boolean {
        val existingDesc = extension().getOwnPropertyDescriptor(name) ?: return false
        return !existingDesc.isConfigurable
    }

    @ECMAImpl("9.1.1.4.15")
    fun canDeclareGlobalVar(name: String): Boolean {
        return extension().let { Operations.hasOwnProperty(it, name.key()) || it.isExtensible() }
    }

    @ECMAImpl("9.1.1.4.16")
    fun canDeclareGlobalFunction(name: String): Boolean {
        val existingProp = extension().getOwnPropertyDescriptor(name) ?: return true
        if (existingProp.isConfigurable)
            return true

        return existingProp.isDataDescriptor && existingProp.isWritable && existingProp.isEnumerable
    }
}
