package me.mattco.reeva.runtime.builtins

import me.mattco.reeva.ast.ParameterList
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.SlotName
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.PropertyKey
import me.mattco.reeva.runtime.primitives.JSAccessor
import me.mattco.reeva.runtime.primitives.JSEmpty
import me.mattco.reeva.runtime.primitives.JSNull
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.ecmaAssert

class JSMappedArgumentsObject private constructor(
    realm: Realm,
    private val envRecord: EnvRecord,
    private val parameters: ParameterList,
    private val arguments: List<JSValue>,
) : JSObject(realm, realm.objectProto) {
    var parameterMap by lateinitSlot<JSObject>(SlotName.ParameterMap)

    override fun init() {
        super.init()

        @Suppress("RemoveRedundantQualifierName")
        parameterMap = JSObject.create(realm, JSNull)

        for ((index, argument) in arguments.withIndex())
            Operations.createDataPropertyOrThrow(realm, this, PropertyKey.from(index), argument)

        val mappedNames = mutableSetOf<String>()

        for (index in parameters.lastIndex downTo 0) {
            val name = parameters[index].identifier.identifierName
            if (name !in mappedNames) {
                mappedNames.add(name)
                if (index < arguments.size) {
                    val getter = makeArgGetter(name)
                    val setter = makeArgSetter(name)

                    parameterMap.defineOwnProperty(index, JSAccessor(getter, setter), Descriptor.CONFIGURABLE)
                }
            }
        }
    }

    override fun getOwnPropertyDescriptor(property: PropertyKey): Descriptor? {
        val desc = super.getOwnPropertyDescriptor(property) ?: return null
        if (Operations.hasOwnProperty(parameterMap, property)) {
            desc.setRawValue(parameterMap.get(property))
        }
        return desc
    }

    override fun defineOwnProperty(property: PropertyKey, descriptor: Descriptor): Boolean {
        val isMapped = Operations.hasOwnProperty(parameterMap, property)
        var newDescriptor = descriptor

        if (isMapped && descriptor.isDataDescriptor) {
            if (descriptor.getRawValue() == JSEmpty && descriptor.hasWritable && !descriptor.isWritable) {
                newDescriptor = descriptor.copy()
                newDescriptor.setRawValue(parameterMap.get(property))
            }
        }

        if (!super.defineOwnProperty(property, newDescriptor))
            return false

        if (isMapped) {
            if (descriptor.isAccessorDescriptor) {
                parameterMap.delete(property)
            } else {
                if (descriptor.getRawValue() != JSEmpty) {
                    val status = parameterMap.set(property, descriptor.getActualValue(realm, this))
                    ecmaAssert(status)
                }
                if (descriptor.hasWritable && !descriptor.isWritable)
                    parameterMap.delete(property)
            }
        }

        return true
    }

    override fun get(property: PropertyKey, receiver: JSValue): JSValue {
        if (!Operations.hasOwnProperty(parameterMap, property))
            return super.get(property, receiver)
        return parameterMap.get(property, parameterMap)
    }

    override fun set(property: PropertyKey, value: JSValue, receiver: JSValue): Boolean {
        val isMapped = if (!this.sameValue(receiver)) {
            false
        } else Operations.hasOwnProperty(parameterMap, property)

        if (isMapped) {
            val status = parameterMap.set(property, value)
            ecmaAssert(status)
        }

        return super.set(property, value, receiver)
    }

    override fun delete(property: PropertyKey): Boolean {
        val isMapped = Operations.hasOwnProperty(parameterMap, property)
        val result = super.delete(property)
        if (result && isMapped)
            parameterMap.delete(property)
        return result
    }

    private fun makeArgGetter(name: String): JSFunction {
        return JSNativeFunction.fromLambda(realm, "", 0) { _, _ ->
            envRecord.getBindingValue(name, false)
        }
    }

    private fun makeArgSetter(name: String): JSFunction {
        return JSNativeFunction.fromLambda(realm, "", 1) { _, args ->
            envRecord.setMutableBinding(name, args.argument(0), false)
            JSUndefined
        }
    }

    companion object {
        fun create(
            realm: Realm,
            envRecord: EnvRecord,
            parameters: ParameterList,
            arguments: List<JSValue>
        ) = JSMappedArgumentsObject(realm, envRecord, parameters, arguments).initialize()
    }
}
