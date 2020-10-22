package me.mattco.reeva.runtime.environment

import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.functions.JSFunction
import me.mattco.reeva.runtime.values.objects.Attributes
import me.mattco.reeva.runtime.values.objects.Descriptor
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.primitives.JSUndefined
import me.mattco.reeva.utils.shouldThrowError

class GlobalEnvRecord(
    val declarativeRecord: DeclarativeEnvRecord,
    val globalThis: JSObject,
    outerEnv: EnvRecord? = null
) : EnvRecord(outerEnv) {
    private val objectRecord = ObjectEnvRecord(globalThis, this)
    private val varNames = mutableListOf<String>()

    @ECMAImpl("HasBinding", "8.1.1.4.1")
    override fun hasBinding(name: String): Boolean {
        return declarativeRecord.hasBinding(name) || objectRecord.hasBinding(name)
    }

    @ECMAImpl("CreateMutableBinding", "8.1.1.4.2")
    override fun createMutableBinding(name: String, canBeDeleted: Boolean) {
        if (declarativeRecord.hasBinding(name))
            shouldThrowError("TypeError")
        return declarativeRecord.createMutableBinding(name, canBeDeleted)
    }

    @ECMAImpl("CreateImmutableBinding", "8.1.1.4.3")
    override fun createImmutableBinding(name: String, throwOnRepeatInitialization: Boolean) {
        if (declarativeRecord.hasBinding(name))
            shouldThrowError("TypeError")
        return declarativeRecord.createImmutableBinding(name, throwOnRepeatInitialization)
    }

    @ECMAImpl("InitializeBinding", "8.1.1.4.4")
    override fun initializeBinding(name: String, value: JSValue) {
        if (declarativeRecord.hasBinding(name))
            return declarativeRecord.initializeBinding(name, value)
        return objectRecord.initializeBinding(name, value)
    }

    @ECMAImpl("SetMutableBinding", "8.1.1.4.5")
    override fun setMutableBinding(name: String, value: JSValue, throwOnFailure: Boolean) {
        if (declarativeRecord.hasBinding(name))
            return declarativeRecord.setMutableBinding(name, value, throwOnFailure)
        return objectRecord.setMutableBinding(name, value, throwOnFailure)
    }

    @ECMAImpl("GetBindingValue", "8.1.1.4.6")
    override fun getBindingValue(name: String, throwOnNotFound: Boolean): JSValue {
        if (declarativeRecord.hasBinding(name))
            return declarativeRecord.getBindingValue(name, throwOnNotFound)
        return objectRecord.getBindingValue(name, throwOnNotFound)
    }

    @ECMAImpl("DeleteBinding", "8.1.1.4.7")
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

    @ECMAImpl("HasThisBinding", "8.1.1.4.8")
    override fun hasThisBinding() = true

    @ECMAImpl("HasSuperBinding", "8.1.1.4.9")
    override fun hasSuperBinding() = false

    @ECMAImpl("WithBaseObject", "8.1.1.4.10")
    override fun withBaseObject() = JSUndefined

    @ECMAImpl("GetThisBinding", "8.1.1.4.11")
    fun getThisBinding() = globalThis

    @ECMAImpl("HasVarDeclaration", "8.1.1.4.12")
    fun hasVarDeclaration(name: String) = name in varNames

    @ECMAImpl("HasLexicalDeclaration", "8.1.1.4.13")
    fun hasLexicalDeclaration(name: String) = declarativeRecord.hasBinding(name)

    @ECMAImpl("HasRestrictedGlobalProperty", "8.1.1.4.14")
    fun hasRestrictedGlobalProperty(name: String): Boolean {
        val existingProp = globalThis.getOwnPropertyDescriptor(name) ?: return false
        if (existingProp.attributes.isConfigurable)
            return false
        return true
    }

    @ECMAImpl("CanDeclareGlobalVar", "8.1.1.4.15")
    fun canDeclareGlobalVar(name: String): Boolean {
        val globalObject = objectRecord.boundObject
        if (globalObject.getOwnPropertyDescriptor(name) != null)
            return true
        return globalObject.isExtensible()
    }

    @ECMAImpl("CanDeclareGlobalFunction", "8.1.1.4.16")
    fun canDeclareGlobalFunction(name: String): Boolean {
        val globalObject = objectRecord.boundObject
        val existingProp = globalObject.getOwnPropertyDescriptor(name) ?: return globalObject.isExtensible()
        if (existingProp.attributes.isConfigurable)
            return true
        if (existingProp.isDataDescriptor && existingProp.attributes.isWritable && existingProp.attributes.isEnumerable)
            return true
        return false
    }

    @ECMAImpl("CreateGlobalVarBinding", "8.1.1.4.17")
    fun createGlobalVarBinding(name: String, canBeDeleted: Boolean) {
        val globalObject = objectRecord.boundObject
        val hasProperty = globalObject.getOwnPropertyDescriptor(name) != null
        if (!hasProperty && globalObject.isExtensible()) {
            objectRecord.createMutableBinding(name, canBeDeleted)
            objectRecord.initializeBinding(name, JSUndefined)
        }
        varNames.add(name)
    }

    @ECMAImpl("CreateGlobalFunctionBinding", "8.1.1.4.18")
    fun createGlobalFunctionBinding(name: String, function: JSFunction, canBeDeleted: Boolean) {
        val globalObject = objectRecord.boundObject
        val existingProp = globalObject.getOwnPropertyDescriptor(name)
        val newDesc = if (existingProp == null || existingProp.attributes.isConfigurable) {
            val attributes = Attributes(Attributes.WRITABLE or Attributes.ENUMERABLE)
            if (canBeDeleted)
                attributes.setConfigurable()
            Descriptor(function, attributes)
        } else {
            Descriptor(function, Attributes(0))
        }
        // TODO: Why do we define _and_ set here?
        if (!globalObject.defineOwnProperty(name, newDesc))
            shouldThrowError("TypeError")
        globalObject.set(name, function)
        varNames.add(name)
    }

    companion object {
        @ECMAImpl("NewGlobalEnvironment", "8.1.2.5")
        fun create(globalObj: JSObject, thisValue: JSObject = globalObj): GlobalEnvRecord {
            val objRecord = ObjectEnvRecord(globalObj, null)
            val declRecord = DeclarativeEnvRecord(null)
            val globalEnv = GlobalEnvRecord(declRecord, thisValue, null)

            // TODO: Are these two lines necessary?
            objRecord.outerEnv = globalEnv
            declRecord.outerEnv = globalEnv

            return globalEnv
        }
    }
}

