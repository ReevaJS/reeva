package com.reevajs.reeva.compiler

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.ECMAScriptFunction
import com.reevajs.reeva.core.environment.ModuleEnvRecord
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.key

abstract class JSCompiledFunction(
    realm: Realm,
    name: String,
    isStrict: Boolean,
) : ECMAScriptFunction(realm, name, isStrict) {
    protected val moduleEnv = Agent.activeAgent.activeEnvRecord as? ModuleEnvRecord

    // TODO: Extract this code and the interpreter code to common place
    protected fun declareGlobals(lexs: Array<String>, funcs: Array<String>, vars: Array<String>) {
        if (moduleEnv != null)
            return

        for (name in lexs) {
            if (hasRestrictedGlobalProperty(name))
                Errors.RestrictedGlobalPropertyName(name).throwSyntaxError(realm)
        }

        for (name in funcs) {
            if (!canDeclareGlobalFunction(name))
                Errors.InvalidGlobalFunction(name).throwSyntaxError(realm)
        }

        for (name in vars) {
            if (!canDeclareGlobalVar(name))
                Errors.InvalidGlobalVar(name).throwSyntaxError(realm)
        }

        for (name in vars)
            realm.globalEnv.setBinding(name, JSUndefined)
        for (name in funcs)
            realm.globalEnv.setBinding(name, JSUndefined)
    }

    @ECMAImpl("9.1.1.4.14")
    private fun hasRestrictedGlobalProperty(name: String): Boolean {
        return realm.globalObject.getOwnPropertyDescriptor(name)?.isConfigurable?.not() ?: false
    }

    @ECMAImpl("9.1.1.4.15")
    private fun canDeclareGlobalVar(name: String): Boolean {
        return Operations.hasOwnProperty(realm.globalObject, name.key()) || realm.globalObject.isExtensible()
    }

    @ECMAImpl("9.1.1.4.16")
    private fun canDeclareGlobalFunction(name: String): Boolean {
        val existingProp = realm.globalObject.getOwnPropertyDescriptor(name)
            ?: return realm.globalObject.isExtensible()

        return when {
            existingProp.isConfigurable -> true
            existingProp.isDataDescriptor && existingProp.isWritable && existingProp.isEnumerable -> true
            else -> false
        }
    }
}