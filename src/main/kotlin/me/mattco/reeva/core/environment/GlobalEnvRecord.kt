package me.mattco.reeva.core.environment

import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.key
import me.mattco.reeva.utils.throwTypeError

class GlobalEnvRecord(
    val declarativeRecord: DeclarativeEnvRecord,
    val globalThis: JSObject,
    outerEnv: EnvRecord? = null
) : EnvRecord(outerEnv) {
    private val objectRecord = ObjectEnvRecord(globalThis, false, this)
    private val varNames = mutableListOf<String>()
    internal var isStrict = false

    @ECMAImpl("8.1.1.4.1")
    override fun hasBinding(name: String): Boolean {
        return declarativeRecord.hasBinding(name) || objectRecord.hasBinding(name)
    }

    @ECMAImpl("8.1.1.4.2")
    override fun createMutableBinding(name: String, canBeDeleted: Boolean) {
        if (declarativeRecord.hasBinding(name)) {
            // TODO: This appears to be a syntax error in spidermonkey
            throwTypeError("redeclaration of lexical variable $name")
        } else {
            declarativeRecord.createMutableBinding(name, canBeDeleted)
        }
    }

    @ECMAImpl("8.1.1.4.3")
    override fun createImmutableBinding(name: String, throwOnRepeatInitialization: Boolean) {
        if (declarativeRecord.hasBinding(name)) {
            throwTypeError("TODO")
        } else {
            declarativeRecord.createImmutableBinding(name, throwOnRepeatInitialization)
        }
    }

    @JSThrows
    @ECMAImpl("8.1.1.4.4")
    override fun initializeBinding(name: String, value: JSValue) {
        if (declarativeRecord.hasBinding(name)) {
            declarativeRecord.initializeBinding(name, value)
        } else objectRecord.initializeBinding(name, value)
    }

    @JSThrows
    @ECMAImpl("8.1.1.4.5")
    override fun setMutableBinding(name: String, value: JSValue, throwOnFailure: Boolean) {
        if (declarativeRecord.hasBinding(name)) {
            declarativeRecord.setMutableBinding(name, value, throwOnFailure)
        } else objectRecord.setMutableBinding(name, value, throwOnFailure)
    }

    @JSThrows
    @ECMAImpl("8.1.1.4.6")
    override fun getBindingValue(name: String, throwOnNotFound: Boolean): JSValue {
        return if (declarativeRecord.hasBinding(name)) {
            declarativeRecord.getBindingValue(name, throwOnNotFound)
        } else objectRecord.getBindingValue(name, throwOnNotFound)
    }

    @ECMAImpl("8.1.1.4.7")
    override fun deleteBinding(name: String): Boolean {
        if (declarativeRecord.hasBinding(name))
            return declarativeRecord.deleteBinding(name)
        if (globalThis.hasProperty(name)) {
            return if (objectRecord.deleteBinding(name)) {
                varNames.remove(name)
                true
            } else false
        }
        return true
    }

    @ECMAImpl("8.1.1.4.8")
    override fun hasThisBinding() = true

    @ECMAImpl("8.1.1.4.9")
    override fun hasSuperBinding() = false

    @ECMAImpl("8.1.1.4.10")
    override fun withBaseObject() = JSUndefined

    @ECMAImpl("8.1.1.4.11")
    fun getThisBinding() = globalThis

    @ECMAImpl("8.1.1.4.12")
    fun hasVarDeclaration(name: String) = name in varNames

    @ECMAImpl("8.1.1.4.13")
    fun hasLexicalDeclaration(name: String) = declarativeRecord.hasBinding(name)

    @ECMAImpl("8.1.1.4.14")
    fun hasRestrictedGlobalProperty(name: String): Boolean {
        val existingProp = globalThis.getOwnPropertyDescriptor(name) ?: return false
        if (existingProp.isConfigurable)
            return false
        return true
    }

    // TODO: Can the global object be a proxy? If not, this method can't throw
    @JSThrows
    @ECMAImpl("8.1.1.4.15")
    fun canDeclareGlobalVar(name: String): Boolean {
        val globalObject = objectRecord.boundObject
        if (globalObject.getOwnPropertyDescriptor(name) != null)
            return true
        return globalObject.isExtensible()
    }

    // TODO: Can the global object be a proxy? If not, this method can't throw
    @ECMAImpl("8.1.1.4.16")
    fun canDeclareGlobalFunction(name: String): Boolean {
        val globalObject = objectRecord.boundObject
        val existingProp = globalObject.getOwnPropertyDescriptor(name) ?: return globalObject.isExtensible()
        if (existingProp.isConfigurable)
            return true
        if (existingProp.isDataDescriptor && existingProp.isWritable && existingProp.isEnumerable)
            return true
        return false
    }

    @JSThrows
    @ECMAImpl("8.1.1.4.17")
    fun createGlobalVarBinding(name: String, canBeDeleted: Boolean) {
        val globalObject = objectRecord.boundObject
        val hasProperty = globalObject.getOwnPropertyDescriptor(name) != null
        if (!hasProperty && globalObject.isExtensible()) {
            objectRecord.createMutableBinding(name, canBeDeleted)
            objectRecord.initializeBinding(name, JSUndefined)
        }
        varNames.add(name)
    }

    @ECMAImpl("8.1.1.4.18")
    fun createGlobalFunctionBinding(name: String, function: JSFunction, canBeDeleted: Boolean) {
        val globalObject = objectRecord.boundObject
        val existingProp = globalObject.getOwnPropertyDescriptor(name)
        val newDesc = if (existingProp == null || existingProp.isConfigurable) {
            Descriptor(function, Descriptor.WRITABLE or Descriptor.ENUMERABLE).also {
                if (canBeDeleted)
                    it.setConfigurable()
            }
        } else {
            Descriptor(function, 0)
        }
        // TODO: Why do we define _and_ set here?
        if (!globalObject.defineOwnProperty(name.key(), newDesc))
            throwTypeError("TODO")
        globalObject.set(name, function)
        varNames.add(name)
    }

    companion object {
        @ECMAImpl("8.1.2.5", "NewGlobalEnvironment")
        fun create(globalObj: JSObject): GlobalEnvRecord {
            val objRecord = ObjectEnvRecord(globalObj, false, null)
            val declRecord = DeclarativeEnvRecord(null)
            val globalEnv = GlobalEnvRecord(declRecord, globalObj, null)

            // TODO: Are these two lines necessary?
            objRecord.outerEnv = globalEnv
            declRecord.outerEnv = globalEnv

            return globalEnv
        }
    }
}

