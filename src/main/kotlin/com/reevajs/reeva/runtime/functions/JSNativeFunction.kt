package com.reevajs.reeva.runtime.functions

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.NativeFunctionSignature
import com.reevajs.reeva.utils.toValue
import java.lang.reflect.InvocationTargetException

abstract class JSNativeFunction protected constructor(
    realm: Realm,
    private val name: String,
    private val length: Int,
    prototype: JSValue = realm.functionProto,
    debugName: String = name,
    private val isConstructor: Boolean = true
) : JSFunction(realm, debugName, prototype = prototype) {
    override fun isConstructor() = isConstructor

    override fun init() {
        super.init()

        defineOwnProperty("length", length.toValue(), Descriptor.CONFIGURABLE)
        defineOwnProperty("name", name.toValue(), Descriptor.CONFIGURABLE)
    }

    companion object {
        fun fromLambda(
            name: String,
            length: Int,
            realm: Realm = Agent.activeAgent.getActiveRealm(),
            lambda: NativeFunctionSignature,
        ) = object : JSNativeFunction(realm, name, length, isConstructor = false) {
            override fun evaluate(arguments: JSArguments): JSValue {
                if (arguments.newTarget != JSUndefined)
                    Errors.NotACtor(name).throwTypeError()
                return try {
                    lambda(arguments)
                } catch (e: InvocationTargetException) {
                    throw e.targetException
                }
            }
        }.initialize()
    }
}
