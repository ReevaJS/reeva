package me.mattco.reeva.runtime.builtins

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.JSValue
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

    private fun checkRevoked(trapName: String) {
        if (isRevoked)
            throwTypeError("Attempt to use revoked Proxy's [[$trapName]] trap")
    }

    private inline fun getTrapOr(name: String, block: () -> Unit): JSValue {
        val trap = Operations.getMethod(handler, name.toValue())
        if (trap == JSUndefined)
            block()
        return trap
    }

    @ECMAImpl("9.5.1")
    override fun getPrototype(): JSValue {
        checkRevoked("GetPrototypeOf")
        val trap = getTrapOr("getPrototypeOf") {
            return target.getPrototype()
        }
        val handlerProto = Operations.call(trap, handler, listOf(target))
        if (handlerProto !is JSObject && handlerProto !is JSNull)
            throwTypeError("Proxy's [[GetPrototypeOf]] did not return an object or null")
        if (target.isExtensible())
            return handlerProto

        val targetProto = target.getPrototype()
        if (!handlerProto.sameValue(targetProto))
            throwTypeError("Proxy's [[GetPrototypeOf]] trap did not return its non-extensible target's prototype")
        return handlerProto
    }

    override fun setPrototype(newPrototype: JSValue): Boolean {
        checkRevoked("SetPrototypeOf")
        ecmaAssert(newPrototype is JSObject || newPrototype is JSNull)
        val trap = getTrapOr("setPrototypeOf") {
            return target.setPrototype(newPrototype)
        }
        val booleanTrapResult = Operations.toBoolean(Operations.call(trap, handler, listOf(target, newPrototype)))
        if (!booleanTrapResult)
            return false
        if (target.isExtensible())
            return true
        val targetProto = target.getPrototype()
        if (!newPrototype.sameValue(targetProto))
            throwTypeError("Proxy's [[SetPrototypeOf]] was not the same value as its non-extensible target's prototype")
        return true
    }

    override fun isExtensible(): Boolean {
        checkRevoked("IsExtensible")
        val trap = getTrapOr("isExtensible") {
            return target.isExtensible()
        }
        val booleanTrapResult = Operations.toBoolean(Operations.call(trap, handler, listOf(target)))
        if (booleanTrapResult != target.isExtensible())
            throwTypeError("Proxy's [[IsExtensible]] trap did not return the same value as its target's [[IsExtensible]] method")
        return booleanTrapResult
    }

    override fun preventExtensions(): Boolean {
        checkRevoked("PreventExtensions")
        val trap = getTrapOr("preventExtensions()") {
            return target.preventExtensions()
        }
        val booleanTrapResult = Operations.toBoolean(Operations.call(trap, handler, listOf(target)))
        if (booleanTrapResult && target.isExtensible())
            throwTypeError("Proxy's [[PreventExtensions]] returned true, but the target is extensible")
        return booleanTrapResult
    }

    override fun getOwnPropertyDescriptor(property: PropertyKey): Descriptor? {
        checkRevoked("GetOwnProperty")
        val trap = getTrapOr("getOwnPropertyDescriptor") {
            return target.getOwnPropertyDescriptor(property)
        }
        val trapResultObj = Operations.call(trap, handler, listOf(target, property.asValue))
        if (trapResultObj !is JSObject && trapResultObj !is JSUndefined)
            throwTypeError("Proxy's [[GetOwnProperty]] trap did not return an object or undefined")
        val targetDesc = target.getOwnPropertyDescriptor(property)
        if (trapResultObj == JSUndefined) {
            if (targetDesc == null)
                return null
            if (!targetDesc.isConfigurable)
                throwTypeError("Proxy's [[GetOwnProperty]] trap reported an existing non-configurable property \"$property\" as non-existent")
            if (!target.isExtensible())
                throwTypeError("Proxy's [[GetOwnProperty]] trap reported non-extensible target's own-property \"$property\" as non-existent")
            return null
        }
        val resultDesc = Descriptor.fromObject(trapResultObj).complete()
        if (!Operations.isCompatiblePropertyDescriptor(target.isExtensible(), resultDesc, targetDesc))
            throwTypeError("Proxy's [[GetOwnProperty]] trap reported non-existent property \"$property\" as existent on non-extensible target")
        if (!resultDesc.isConfigurable) {
            if (targetDesc == null)
                throwTypeError("Proxy's [[GetOwnProperty]] trap reported non-existent property \"$property\" as non-configurable")
            if (targetDesc.isConfigurable)
                throwTypeError("Proxy's [[GetOwnProperty]] trap reported configurable property \"$property\" as non-configurable")
            if (resultDesc.hasWritable && !resultDesc.isWritable && targetDesc.isWritable)
                throwTypeError("Proxy's [[GetOwnProperty]] trap reported writable property \"$property\" as non-configurable and non-writable")
        }
        return resultDesc
    }

    override fun defineOwnProperty(property: PropertyKey, descriptor: Descriptor): Boolean {
        checkRevoked("DefineProperty")
        val trap = getTrapOr("defineProperty") {
            return target.defineOwnProperty(property, descriptor)
        }

        val descObj = descriptor.toObject(realm, target)
        val booleanTrapResult = Operations.toBoolean(Operations.call(trap, handler, listOf(target, property.asValue, descObj)))
        if (!booleanTrapResult)
            return false
        val targetDesc = target.getOwnPropertyDescriptor(property)
        val isExtensible = target.isExtensible()
        val settingConfigFalse = descriptor.hasConfigurable && !descriptor.isConfigurable
        if (targetDesc == null) {
            if (!isExtensible)
                throwTypeError("Proxy's [[DefineProperty]] trap added property \"$property\" to non-extensible target")
            if (settingConfigFalse)
                throwTypeError("Proxy's [[DefineProperty]] trap added previously non-existent property \"$property\" to target as non-configurable")
        } else {
            if (!Operations.isCompatiblePropertyDescriptor(isExtensible, descriptor, targetDesc))
                throwTypeError("Proxy's [[DefineProperty]] trap added property \"$property\" to the target with an incompatible descriptor to the existing property")
            if (settingConfigFalse && targetDesc.isConfigurable)
                throwTypeError("Proxy's [[DefineProperty]] trap overwrote existing configurable property \"$property\" to be non-configurable")
            if (targetDesc.isDataDescriptor && !targetDesc.isConfigurable && targetDesc.isWritable && descriptor.hasWritable && !descriptor.isWritable)
                throwTypeError("Proxy's [[DefineProperty]] trap added a writable property \"$property\" in place of an existing non-configurable, writable property")
        }
        return true
    }

    override fun hasProperty(property: PropertyKey): Boolean {
        checkRevoked("Has")
        val trap = getTrapOr("has") {
            return target.hasProperty(property)
        }
        val booleanTrapResult = Operations.toBoolean(Operations.call(trap, handler, listOf(target, property.asValue)))
        if (!booleanTrapResult) {
            val targetDesc = target.getOwnPropertyDescriptor(property)
            if (targetDesc != null) {
                if (!targetDesc.isConfigurable)
                    throwTypeError("Proxy's [[Has]] trap reported existing non-configurable property \"$property\" as non-existent")
                if (!target.isExtensible())
                    throwTypeError("Proxy's [[Has]] trap reported existing property \"$property\" of non-extensible target as non-existent")
            }
        }
        return booleanTrapResult
    }

    override fun get(property: PropertyKey, receiver: JSValue): JSValue {
        checkRevoked("Get")
        val trap = getTrapOr("get") {
            return target.get(property, receiver)
        }
        val trapResult = Operations.call(trap, handler, listOf(target, property.asValue, receiver))
        val targetDesc = target.getOwnPropertyDescriptor(property)
        if (targetDesc != null && !targetDesc.isConfigurable) {
            if (targetDesc.isDataDescriptor && !targetDesc.isWritable && !trapResult.sameValue(targetDesc.getRawValue()))
                throwTypeError("Proxy's [[Get]] trap reported a different value from the existing non-configurable, non-writable own property \"$property\"")
            if (targetDesc.isAccessorDescriptor && !targetDesc.hasGetter && trapResult != JSUndefined)
                throwTypeError("Proxy's [[Get]] trap reported a non-undefined value for existing non-configurable accessor property \"$property\" with an undefined getter")
        }
        return trapResult
    }

    override fun set(property: PropertyKey, value: JSValue, receiver: JSValue): Boolean {
        checkRevoked("Set")
        val trap = getTrapOr("set") {
            return target.set(property, value, receiver)
        }
        val trapResult = Operations.toBoolean(Operations.call(trap, handler, listOf(target, property.asValue, receiver)))
        if (!trapResult)
            return false
        val targetDesc = target.getOwnPropertyDescriptor(property)
        if (targetDesc != null && !targetDesc.isConfigurable) {
            if (targetDesc.isDataDescriptor && !targetDesc.isWritable && !value.sameValue(targetDesc.getRawValue()))
                throwTypeError("Proxy's [[Set]] trap changed the value of the non-configurable, non-writable own property \"$property\"")
            if (targetDesc.isAccessorDescriptor && !targetDesc.hasSetter)
                throwTypeError("Proxy's [[Set]] trap changed the value of the non-configurable accessor property \"$property\" with an undefined setter")
        }
        return true
    }

    override fun delete(property: PropertyKey): Boolean {
        checkRevoked("Delete")
        val trap = getTrapOr("delete") {
            return target.delete(property)
        }
        val booleanTrapResult = Operations.toBoolean(Operations.call(trap, handler, listOf(target, property.asValue)))
        if (!booleanTrapResult)
            return false
        val targetDesc = target.getOwnPropertyDescriptor(property) ?: return true
        if (!targetDesc.isConfigurable)
            throwTypeError("Proxy's [[Delete]] trap deleted non-configurable property \"$property\" from its target")
        if (!target.isExtensible())
            throwTypeError("Proxy's [[Delete]] trap delete existing property \"$property\" from its non-extensible target")
        return true
    }

    override fun ownPropertyKeys(): List<PropertyKey> {
        checkRevoked("OwnKeys")
        val trap = getTrapOr("ownKeys") {
            return target.ownPropertyKeys()
        }
        val trapResultArray = Operations.call(trap, handler, listOf(target))
        // Spec deviation: We use numbers as keys, so we need to include the number type in this list
        val trapResult = Operations.createListFromArrayLike(
            trapResultArray,
            listOf(Type.String, Type.Symbol, Type.Number)
        ).map {
            PropertyKey.from(it)!!
        }
        if (trapResult.distinct().size != trapResult.size)
            throwTypeError("Proxy's [[OwnKeys]] trap reported duplicate property keys")
        val isExtensible = target.isExtensible()
        val targetKeys = target.ownPropertyKeys()
        val targetConfigurableKeys = mutableListOf<PropertyKey>()
        val targetNonconfigurableKeys = mutableListOf<PropertyKey>()
        targetKeys.forEach { key ->
            val desc = target.getOwnPropertyDescriptor(key)
            if (desc != null && !desc.isConfigurable) {
                targetNonconfigurableKeys.add(key)
            } else targetConfigurableKeys.add(key)
        }
        if (isExtensible && targetNonconfigurableKeys.isEmpty())
            return trapResult

        val uncheckedResultKeys = trapResult.toMutableList()
        targetNonconfigurableKeys.forEach { key ->
            if (key !in uncheckedResultKeys)
                throwTypeError("Proxy's [[OwnKeys]] trap failed to report non-configurable property \"$key\"")
            uncheckedResultKeys.remove(key)
        }
        if (isExtensible)
            return trapResult
        targetConfigurableKeys.forEach { key ->
            if (key !in uncheckedResultKeys)
                throwTypeError("Proxy's [[OwnKeys]] trap failed to report property \"$key\" of non-extensible target")
            uncheckedResultKeys.remove(key)
        }
        if (uncheckedResultKeys.isNotEmpty())
            throwTypeError("Proxy's [[OwnKeys]] reported extra property keys for its non-extensible target")
        return trapResult
    }

    fun call(thisValue: JSValue, arguments: JSArguments): JSValue {
        checkRevoked("Call")
        expect(isCallable)
        val trap = getTrapOr("apply") {
            return Operations.call(target, thisValue, arguments)
        }
        val argArray = Operations.createArrayFromList(arguments)
        return Operations.call(trap, thisValue, listOf(target, thisValue, argArray))
    }

    fun construct(arguments: JSArguments, newTarget: JSValue): JSValue {
        checkRevoked("Construct")
        expect(isConstructor)
        val trap = getTrapOr("construct") {
            return Operations.construct(target, arguments, newTarget)
        }
        val argArray = Operations.createArrayFromList(arguments)
        val newObj = Operations.call(trap, handler, listOf(target, argArray, newTarget))
        if (newObj !is JSObject)
            throwTypeError("Proxy's [[Construct]] trap returned a non-object value")
        return newObj
    }

    companion object {
        fun create(realm: Realm, target: JSObject, handler: JSObject) =
            JSProxyObject(realm, target, handler).also { it.init() }
    }
}
