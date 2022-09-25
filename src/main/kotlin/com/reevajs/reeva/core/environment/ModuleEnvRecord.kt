package com.reevajs.reeva.core.environment

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.lifecycle.ModuleRecord
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.ecmaAssert
import com.reevajs.reeva.utils.unreachable

class ModuleEnvRecord(realm: Realm, outer: EnvRecord?) : DeclarativeEnvRecord(realm, outer) {
    @ECMAImpl("9.1.1.5.1")
    override fun getBindingValue(name: String, isStrict: Boolean): JSValue {
        // 1. Assert: S is true.
        ecmaAssert(isStrict)

        // 2. Assert: envRec has a binding for N.
        val binding = bindings[name]
        ecmaAssert(binding != null)

        // 3. If the binding for N is an indirect binding, then
        if (binding is IndirectBinding) {
            // a. Let M and N2 be the indirection values provided when this binding for N was created.

            // b. Let targetEnv be M.[[Environment]].
            // c. If targetEnv is empty, throw a ReferenceError exception.
            val targetEnv = binding.sourceModule.environment
                ?: Errors.TODO("ModuleEnvRecord::getBindingValue 1").throwReferenceError(realm)

            // d. Return ? targetEnv.GetBindingValue(N2, true).
            return targetEnv.getBindingValue(binding.sourceName, true)
        }

        // 4. If the binding for N in envRec is an uninitialized binding, throw a ReferenceError exception.
        if (binding.value == null) {
            Errors.TODO("ModuleEnvRecord::getBindingValue 2").throwReferenceError(realm)
        }

        // 5. Return the value currently bound to N in envRec.
        return binding.value!!
    }

    @ECMAImpl("9.1.1.5.2")
    override fun deleteBinding(name: String): Boolean {
        // The DeleteBinding concrete method of a module Environment Record is never used within this specification.
        unreachable()
    }

    @ECMAImpl("9.1.1.5.3")
    override fun hasThisBinding(): Boolean {
        // 1. Return true
        return true
    }

    @ECMAImpl("9.1.1.5.4")
    fun getThisBinding(): JSValue {
        // 1. Return undefined.
        return JSUndefined
    }

    @ECMAImpl("9.1.1.5.4")
    fun createImportBinding(name: String, record: ModuleRecord, sourceName: String) {
        // 1. Assert: envRec does not already have a binding for N.
        ecmaAssert(!hasBinding(name))

        // 2. Assert: When M.[[Environment]] is instantiated it will have a direct binding for N2.

        // 3. Create an immutable indirect binding in envRec for N that references M and N2 as its target binding and
        //    record that the binding is initialized.
        bindings[name] = IndirectBinding(sourceName, record)

        // 4. Return unused.
    }
}
