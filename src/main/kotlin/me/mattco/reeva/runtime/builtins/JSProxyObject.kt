package me.mattco.reeva.runtime.builtins

import me.mattco.reeva.core.Agent.Companion.ifError
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.PropertyKey
import me.mattco.reeva.runtime.primitives.JSFalse
import me.mattco.reeva.runtime.primitives.JSNull
import me.mattco.reeva.runtime.primitives.JSTrue
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.*

class JSProxyObject private constructor(
    realm: Realm,
    val target: JSObject,
    val handler: JSObject,
) : JSObject(realm, realm.objectProto) {
    val isCallable = Operations.isCallable(target)
    val isConstructor = Operations.isConstructor(target)

    private var isRevoked = false

    fun revoke() {
        expect(!isRevoked)
        isRevoked = true
    }

    private inline fun ifRevoked(trapName: String, block: () -> Unit) {
        if (isRevoked) {
            throwError<JSTypeErrorObject>("Attempt to use revoked Proxy's [[$trapName]] trap")
            block()
        }
    }

    private inline fun getTrapOr(name: String, block: () -> Unit): JSValue {
        val trap = Operations.getMethod(handler, name.toValue())
        if (trap == JSUndefined)
            block()
        return trap
    }

    @ECMAImpl("9.5.1")
    override fun getPrototype(): JSValue {
        ifRevoked("GetPrototypeOf") { return INVALID_VALUE }

        val trap = getTrapOr("getPrototypeOf") {
            return target.getPrototype()
        }
        val handlerProto = Operations.call(trap, handler, listOf(target))
        ifError { return INVALID_VALUE }
        if (handlerProto !is JSObject && handlerProto !is JSNull) {
            throwError<JSTypeErrorObject>("Proxy's [[GetPrototypeOf]] did not return an object or null")
            return INVALID_VALUE
        }
        if (target.isExtensible())
            return handlerProto

        val targetProto = target.getPrototype()
        ifError { return INVALID_VALUE }
        if (!handlerProto.sameValue(targetProto)) {
            throwError<JSTypeErrorObject>("Proxy's [[GetPrototypeOf]] trap did not return its non-extensible target's prototype")
            return INVALID_VALUE
        }
        return handlerProto
    }

    override fun setPrototype(newPrototype: JSValue): Boolean {
        ifRevoked("SetPrototypeOf") { return false }

        ecmaAssert(newPrototype is JSObject || newPrototype is JSNull)
        val trap = getTrapOr("setPrototypeOf") {
            return target.setPrototype(newPrototype)
        }
        val booleanTrapResult = Operations.toBoolean(Operations.call(trap, handler, listOf(target, newPrototype)).also {
            ifError { return false }
        })
        if (booleanTrapResult == JSFalse)
            return false
        if (target.isExtensible())
            return true
        val targetProto = target.getPrototype()
        if (!newPrototype.sameValue(targetProto)) {
            throwError<JSTypeErrorObject>("Proxy's [[SetPrototypeOf]] was not the same value as its non-extensible target's prototype")
            return false
        }
        return true
    }

    override fun isExtensible(): Boolean {
        ifRevoked("IsExtensible") { return false }

        val trap = getTrapOr("isExtensible") {
            return target.isExtensible()
        }
        val booleanTrapResult = Operations.toBoolean(Operations.call(trap, handler, listOf(target)).also {
            ifError { return false }
        })
        if ((booleanTrapResult == JSTrue) != target.isExtensible()) {
            throwError<JSTypeErrorObject>("Proxy's [[IsExtensible]] trap did not return the same value as its target's [[IsExtensible]] method")
            return false
        }
        return booleanTrapResult == JSTrue
    }

    override fun preventExtensions(): Boolean {
        ifRevoked("PreventExtensions") { return false }

        val trap = getTrapOr("preventExtensions()") {
            return target.preventExtensions()
        }
        val booleanTrapResult = Operations.toBoolean(Operations.call(trap, handler, listOf(target)).also {
            ifError { return false }
        })
        if (booleanTrapResult == JSTrue && target.isExtensible()) {
            throwError<JSTypeErrorObject>("Proxy's [[PreventExtensions]] returned true, but the target is extensible")
            return false
        }
        return booleanTrapResult == JSTrue
    }

    override fun getOwnPropertyDescriptor(property: PropertyKey): Descriptor? {
        ifRevoked("GetOwnProperty") { return null }

        val trap = getTrapOr("getOwnPropertyDescriptor") {
            return target.getOwnPropertyDescriptor(property)
        }
        val trapResultObj = Operations.call(trap, handler, listOf(target, property.asValue))
        ifError { return null }
        if (trapResultObj !is JSObject && trapResultObj !is JSUndefined) {
            throwError<JSTypeErrorObject>("Proxy's [[GetOwnProperty]] trap did not return an object or undefined")
            return null
        }
        val targetDesc = target.getOwnPropertyDescriptor(property)
        ifError { return null }
        if (trapResultObj == JSUndefined) {
            if (targetDesc == null)
                return null
            if (!targetDesc.isConfigurable) {
                throwError<JSTypeErrorObject>("Proxy's [[GetOwnProperty]] trap reported an existing non-configurable property \"$property\" as non-existent")
                return null
            }
            if (!target.isExtensible())
                throwError<JSTypeErrorObject>("Proxy's [[GetOwnProperty]] trap reported non-extensible target's own-property \"$property\" as non-existent")
            return null
        }
        val resultDesc = Descriptor.fromObject(trapResultObj).complete()
        ifError { return null }
        if (!Operations.isCompatiblePropertyDescriptor(target.isExtensible(), resultDesc, targetDesc)) {
            throwError<JSTypeErrorObject>("Proxy's [[GetOwnProperty]] trap reported non-existent property \"$property\" as existent on non-extensible target")
            return null
        }
        if (!resultDesc.isConfigurable) {
            if (targetDesc == null) {
                throwError<JSTypeErrorObject>("Proxy's [[GetOwnProperty]] trap reported non-existent property \"$property\" as non-configurable")
                return null
            }
            if (targetDesc.isConfigurable) {
                throwError<JSTypeErrorObject>("Proxy's [[GetOwnProperty]] trap reported configurable property \"$property\" as non-configurable")
                return null
            }
            if (resultDesc.hasWritable && !resultDesc.isWritable && targetDesc.isWritable) {
                throwError<JSTypeErrorObject>("Proxy's [[GetOwnProperty]] trap reported writable property \"$property\" as non-configurable and non-writable")
                return null
            }
        }
        return resultDesc
    }

    override fun defineOwnProperty(property: PropertyKey, descriptor: Descriptor): Boolean {
        ifRevoked("DefineProperty") { return false }

        val trap = getTrapOr("defineProperty") {
            return target.defineOwnProperty(property, descriptor)
        }

        val descObj = descriptor.toObject(realm, target)
        val booleanTrapResult = Operations.toBoolean(Operations.call(trap, handler, listOf(target, property.asValue, descObj)).also {
            ifError { return false }
        })
        if (booleanTrapResult == JSFalse)
            return false
        val targetDesc = target.getOwnPropertyDescriptor(property)
        ifError { return false }
        val isExtensible = target.isExtensible()
        ifError { return false }
        val settingConfigFalse = descriptor.hasConfigurable && !descriptor.isConfigurable
        if (targetDesc == null) {
            if (!isExtensible) {
                throwError<JSTypeErrorObject>("Proxy's [[DefineProperty]] trap added property \"$property\" to non-extensible target")
                return false
            }
            if (settingConfigFalse) {
                throwError<JSTypeErrorObject>("Proxy's [[DefineProperty]] trap added previously non-existent property \"$property\" to target as non-configurable")
                return false
            }
        } else {
            if (!Operations.isCompatiblePropertyDescriptor(isExtensible, descriptor, targetDesc)) {
                throwError<JSTypeErrorObject>("Proxy's [[DefineProperty]] trap added property \"$property\" to the target with an incompatible descriptor to the existing property")
                return false
            }
            if (settingConfigFalse && targetDesc.isConfigurable) {
                throwError<JSTypeErrorObject>("Proxy's [[DefineProperty]] trap overwrote existing configurable property \"$property\" to be non-configurable")
                return false
            }
            if (targetDesc.isDataDescriptor && !targetDesc.isConfigurable && targetDesc.isWritable && descriptor.hasWritable && !descriptor.isWritable) {
                throwError<JSTypeErrorObject>("Proxy's [[DefineProperty]] trap added a writable property \"$property\" in place of an existing non-configurable, writable property")
                return false
            }
        }
        return true
    }

    override fun hasProperty(property: PropertyKey): Boolean {
        ifRevoked("Has") { return false }

        val trap = getTrapOr("has") {
            return target.hasProperty(property)
        }
        val booleanTrapResult = Operations.toBoolean(Operations.call(trap, handler, listOf(target, property.asValue)).also {
            ifError { return false }
        })
        if (booleanTrapResult == JSFalse) {
            val targetDesc = target.getOwnPropertyDescriptor(property)
            ifError { return false }
            if (targetDesc != null) {
                if (!targetDesc.isConfigurable) {
                    throwError<JSTypeErrorObject>("Proxy's [[Has]] trap reported existing non-configurable property \"$property\" as non-existent")
                    return false
                }
                if (!target.isExtensible()) {
                    throwError<JSTypeErrorObject>("Proxy's [[Has]] trap reported existing property \"$property\" of non-extensible target as non-existent")
                    return false
                }
            }
        }
        return booleanTrapResult == JSTrue
    }

    override fun get(property: PropertyKey, receiver: JSValue): JSValue {
        ifRevoked("Get") { return INVALID_VALUE }

        val trap = getTrapOr("get") {
            return target.get(property, receiver)
        }
        val trapResult = Operations.call(trap, handler, listOf(target, property.asValue, receiver).also {
            ifError { return INVALID_VALUE }
        })
        val targetDesc = target.getOwnPropertyDescriptor(property)
        ifError { return INVALID_VALUE }
        if (targetDesc != null && !targetDesc.isConfigurable) {
            if (targetDesc.isDataDescriptor && !targetDesc.isWritable && !trapResult.sameValue(targetDesc.getRawValue())) {
                throwError<JSTypeErrorObject>("Proxy's [[Get]] trap reported a different value from the existing non-configurable, non-writable own property \"$property\"")
                return INVALID_VALUE
            }
            if (targetDesc.isAccessorDescriptor && !targetDesc.hasGetter && trapResult != JSUndefined) {
                throwError<JSTypeErrorObject>("Proxy's [[Get]] trap reported a non-undefined value for existing non-configurable accessor property \"$property\" with an undefined getter")
            }
        }
        return trapResult
    }

    override fun set(property: PropertyKey, value: JSValue, receiver: JSValue): Boolean {
        ifRevoked("Set") { return false }

        val trap = getTrapOr("set") {
            return target.set(property, value, receiver)
        }
        val trapResult = Operations.toBoolean(Operations.call(trap, handler, listOf(target, property.asValue, receiver).also {
            ifError { return false }
        }))
        if (trapResult == JSFalse)
            return false
        val targetDesc = target.getOwnPropertyDescriptor(property)
        ifError { return false }
        if (targetDesc != null && !targetDesc.isConfigurable) {
            if (targetDesc.isDataDescriptor && !targetDesc.isWritable && !value.sameValue(targetDesc.getRawValue())) {
                throwError<JSTypeErrorObject>("Proxy's [[Set]] trap changed the value of the non-configurable, non-writable own property \"$property\"")
                return false
            }
            if (targetDesc.isAccessorDescriptor && !targetDesc.hasSetter) {
                throwError<JSTypeErrorObject>("Proxy's [[Set]] trap changed the value of the non-configurable accessor property \"$property\" with an undefined setter")
            }
        }
        return true
    }

    override fun delete(property: PropertyKey): Boolean {
        ifRevoked("Delete") { return false }

        val trap = getTrapOr("delete") {
            return target.delete(property)
        }
        val booleanTrapResult = Operations.toBoolean(Operations.call(trap, handler, listOf(target, property.asValue)).also {
            ifError { return false }
        })
        if (booleanTrapResult == JSFalse)
            return false
        val targetDesc = target.getOwnPropertyDescriptor(property) ?: return true
        ifError { return false }
        if (!targetDesc.isConfigurable) {
            throwError<JSTypeErrorObject>("Proxy's [[Delete]] trap deleted non-configurable property \"$property\" from its target")
            return false
        }
        if (!target.isExtensible()) {
            throwError<JSTypeErrorObject>("Proxy's [[Delete]] trap delete existing property \"$property\" from its non-extensible target")
            return false
        }
        return true
    }

    override fun ownPropertyKeys(): List<PropertyKey> {
        ifRevoked("OwnKeys") { return emptyList() }

        val trap = getTrapOr("ownKeys") {
            return target.ownPropertyKeys()
        }
        val trapResultArray = Operations.call(trap, handler, listOf(target))
        // Spec deviation: We use numbers as keys, so we need to include the number type in this list
        val trapResult = Operations.createListFromArrayLike(
            trapResultArray,
            listOf(Type.String, Type.Symbol, Type.Number)
        ).also {
            ifError { return emptyList() }
        }.map {
            PropertyKey.from(it)!!
        }
        if (trapResult.distinct().size != trapResult.size) {
            throwError<JSTypeErrorObject>("Proxy's [[OwnKeys]] trap reported duplicate property keys")
            return emptyList()
        }
        val isExtensible = target.isExtensible()
        val targetKeys = target.ownPropertyKeys()
        ifError { return emptyList() }
        val targetConfigurableKeys = mutableListOf<PropertyKey>()
        val targetNonconfigurableKeys = mutableListOf<PropertyKey>()
        targetKeys.forEach { key ->
            val desc = target.getOwnPropertyDescriptor(key)
            ifError { return emptyList() }
            if (desc != null && !desc.isConfigurable) {
                targetNonconfigurableKeys.add(key)
            } else targetConfigurableKeys.add(key)
        }
        if (isExtensible && targetNonconfigurableKeys.isEmpty())
            return trapResult

        val uncheckedResultKeys = trapResult.toMutableList()
        targetNonconfigurableKeys.forEach { key ->
            if (key !in uncheckedResultKeys) {
                throwError<JSTypeErrorObject>("Proxy's [[OwnKeys]] trap failed to report non-configurable property \"$key\"")
                return emptyList()
            }
            uncheckedResultKeys.remove(key)
        }
        if (isExtensible)
            return trapResult
        targetConfigurableKeys.forEach { key ->
            if (key !in uncheckedResultKeys) {
                throwError<JSTypeErrorObject>("Proxy's [[OwnKeys]] trap failed to report property \"$key\" of non-extensible target")
                return emptyList()
            }
            uncheckedResultKeys.remove(key)
        }
        if (uncheckedResultKeys.isNotEmpty()) {
            throwError<JSTypeErrorObject>("Proxy's [[OwnKeys]] reported extra property keys for its non-extensible target")
            return emptyList()
        }
        return trapResult
    }

    fun call(thisValue: JSValue, arguments: JSArguments): JSValue {
        ifRevoked("Call") { return INVALID_VALUE }

        expect(isCallable)
        val trap = getTrapOr("apply") {
            return Operations.call(target, thisValue, arguments)
        }
        val argArray = Operations.createArrayFromList(arguments)
        ifError { return INVALID_VALUE }
        return Operations.call(target, thisValue, listOf(target, thisValue, argArray))
    }

    fun construct(arguments: JSArguments, newTarget: JSValue): JSValue {
        ifRevoked("Construct") { return INVALID_VALUE }

        expect(isConstructor)
        val trap = getTrapOr("construct") {
            return Operations.construct(target, arguments, newTarget)
        }
        val argArray = Operations.createArrayFromList(arguments)
        val newObj = Operations.call(trap, handler, listOf(target, argArray, newTarget))
        if (newObj !is JSObject) {
            throwError<JSTypeErrorObject>("Proxy's [[Construct]] trap returned a non-object value")
            return INVALID_VALUE
        }
        return newObj
    }

    companion object {
        fun create(realm: Realm, target: JSObject, handler: JSObject) =
            JSProxyObject(realm, target, handler).also { it.init() }
    }
}
