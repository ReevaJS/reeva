package me.mattco.reeva.core.environment

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.PropertyKey
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors

class GlobalEnvRecord(realm: Realm, isStrict: Boolean) : EnvRecord(realm, isStrict) {
    private val objectRecord = ObjectEnvRecord(realm, isStrict, realm.globalObject, isWithEnvironment = false)
    private val declarativeRecord = DeclarativeEnvRecord(realm, isStrict)
    private val varNames = mutableSetOf<String>()

    override fun hasBinding(name: String): Boolean {
        return declarativeRecord.hasBinding(name) || objectRecord.hasBinding(name)
    }

    override fun createMutableBinding(name: String, deletable: Boolean) {
        if (declarativeRecord.hasBinding(name))
            Errors.TODO("GlobalEnvRecord::createMutableBinding").throwTypeError(realm)
        declarativeRecord.createMutableBinding(name, deletable)
    }

    override fun createImmutableBinding(name: String, strict: Boolean) {
        if (declarativeRecord.hasBinding(name))
            Errors.TODO("GlobalEnvRecord::createImmutableBinding").throwTypeError(realm)
        declarativeRecord.createImmutableBinding(name, strict)
    }

    override fun initializeBinding(name: String, value: JSValue) {
        if (declarativeRecord.hasBinding(name)) {
            declarativeRecord.initializeBinding(name, value)
            return
        }

        objectRecord.initializeBinding(name, value)
    }

    override fun setMutableBinding(name: String, value: JSValue, strict: Boolean) {
        if (declarativeRecord.hasBinding(name)) {
            declarativeRecord.setMutableBinding(name, value, strict)
            return
        }

        objectRecord.setMutableBinding(name, value, strict)
    }

    override fun getBindingValue(name: String, strict: Boolean): JSValue {
        if (declarativeRecord.hasBinding(name))
            return declarativeRecord.getBindingValue(name, strict)
        return objectRecord.getBindingValue(name, strict)
    }

    override fun deleteBinding(name: String): Boolean {
        if (declarativeRecord.hasBinding(name))
            return declarativeRecord.deleteBinding(name)

        val globalObject = objectRecord.bindingObject
        if (globalObject.hasProperty(name)) {
            val status = objectRecord.deleteBinding(name)
            if (status)
                varNames.remove(name)
            return status
        }

        return true
    }

    override fun hasThisBinding() = true

    override fun hasSuperBinding() = false

    override fun withBaseObject(): JSObject? = null

    fun getThisBinding(): JSValue = objectRecord.bindingObject

    fun hasVarDeclaration(name: String) = name in varNames

    fun hasLexicalDeclaration(name: String) = declarativeRecord.hasBinding(name)

    fun hasRestrictedGlobalProperty(name: String): Boolean {
        val globalObject = objectRecord.bindingObject
        val existingProp = globalObject.getOwnPropertyDescriptor(name) ?: return false
        return !existingProp.isConfigurable
    }

    fun canDeclareGlobalVar(name: String): Boolean {
        val globalObject = objectRecord.bindingObject
        if (globalObject.hasProperty(name))
            return true
        return globalObject.isExtensible()
    }

    fun canDeclareGlobalFunction(name: String): Boolean {
        val globalObject = objectRecord.bindingObject
        val existingProp = globalObject.getOwnPropertyDescriptor(name) ?: return globalObject.isExtensible()
        if (existingProp.isConfigurable)
            return true
        return existingProp.isDataDescriptor && existingProp.isEnumerable && existingProp.isWritable
    }

    fun createGlobalVarBinding(name: String, deletable: Boolean) {
        val globalObject = objectRecord.bindingObject
        if (!Operations.hasOwnProperty(globalObject, PropertyKey.from(name)) && globalObject.isExtensible()) {
            objectRecord.createMutableBinding(name, deletable)
            objectRecord.initializeBinding(name, JSUndefined)
        }

        varNames.add(name)
    }

    fun createGlobalFunctionBinding(name: String, value: JSValue, deletable: Boolean) {
        val globalObject = objectRecord.bindingObject
        val existingProp = globalObject.getOwnPropertyDescriptor(name)

        val desc = if (existingProp == null || existingProp.isConfigurable) {
            var attributes = Descriptor.WRITABLE or Descriptor.ENUMERABLE
            if (deletable)
                attributes = attributes or Descriptor.CONFIGURABLE
            Descriptor(value, attributes)
        } else Descriptor(value, 0)

        Operations.definePropertyOrThrow(realm, globalObject, PropertyKey.from(name), desc)
        Operations.set(realm, globalObject, PropertyKey.from(name), value, false)
        varNames.add(name)
    }


}
