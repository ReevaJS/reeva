package me.mattco.jsthing.runtime.environment

import me.mattco.jsthing.runtime.JSGlobalObject
import me.mattco.jsthing.runtime.annotations.ECMAImpl
import me.mattco.jsthing.runtime.values.JSValue
import me.mattco.jsthing.runtime.values.nonprimitives.functions.JSFunction
import me.mattco.jsthing.runtime.values.nonprimitives.objects.JSObject
import me.mattco.jsthing.runtime.values.primitives.JSUndefined
import me.mattco.jsthing.utils.shouldThrowError

class GlobalEnvRecord(
    val declarativeRecord: DeclarativeEnvRecord,
    val globalThis: JSObject,
    outerEnv: EnvRecord? = null
) : EnvRecord(outerEnv) {
    val objectRecord = ObjectEnvRecord(globalThis, this)
    private val varNames = mutableListOf<String>()

    override fun hasBinding(name: String): Boolean {
        return declarativeRecord.hasBinding(name) || objectRecord.hasBinding(name)
    }

    override fun createMutableBinding(name: String, canBeDeleted: Boolean) {
        if (declarativeRecord.hasBinding(name))
            shouldThrowError("TypeError")
        return declarativeRecord.createMutableBinding(name, canBeDeleted)
    }

    override fun createImmutableBinding(name: String, throwOnRepeatInitialization: Boolean) {
        if (declarativeRecord.hasBinding(name))
            shouldThrowError("TypeError")
        return declarativeRecord.createImmutableBinding(name, throwOnRepeatInitialization)
    }

    override fun initializeBinding(name: String, value: JSValue) {
        if (declarativeRecord.hasBinding(name))
            return declarativeRecord.initializeBinding(name, value)
        return objectRecord.initializeBinding(name, value)
    }

    override fun setMutableBinding(name: String, value: JSValue, throwOnFailure: Boolean) {
        if (declarativeRecord.hasBinding(name))
            return declarativeRecord.setMutableBinding(name, value, throwOnFailure)
        return objectRecord.setMutableBinding(name, value, throwOnFailure)
    }

    override fun getBindingValue(name: String, throwOnNotFound: Boolean): JSValue {
        if (declarativeRecord.hasBinding(name))
            return declarativeRecord.getBindingValue(name, throwOnNotFound)
        return objectRecord.getBindingValue(name, throwOnNotFound)
    }

    override fun deleteBinding(name: String): Boolean {
        if (declarativeRecord.hasBinding(name))
            return declarativeRecord.deleteBinding(name)
        if (globalThis.hasOwnProperty(name)) {
            return if (objectRecord.deleteBinding(name)) {
                varNames.remove(name)
                true
            } else false
        }
        return true
    }

    override fun hasThisBinding() = true

    override fun hasSuperBinding() = false

    override fun withBaseObject() = JSUndefined

    fun getThisBinding() = globalThis

    fun hasVarDeclaration(name: String) = name in varNames

    fun hasLexicalDeclaration(name: String) = declarativeRecord.hasBinding(name)

    fun hasRestrictedGlobalProperty(name: String): Boolean {
        val existingProp = globalThis.getOwnPropertyDescriptor(name) ?: return false
        if (existingProp.attributes.isConfigurable)
            return false
        return true
    }

    fun canDeclareGlobalVar(name: String): Boolean {
        TODO()
    }

    fun canDeclareGlobalFunction(name: String): Boolean {
        TODO()
    }

    fun createGlobalVarBinding(name: String, canBeDeleted: Boolean) {
        TODO()
    }

    fun createGlobalFunctionBinding(name: String, function: JSFunction, canBeDeleted: Boolean) {
        TODO()
    }

    companion object {
        fun create(globalObj: JSGlobalObject, thisValue: JSObject = globalObj): GlobalEnvRecord {
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

