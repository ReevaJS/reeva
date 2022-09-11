package com.reevajs.reeva.core.environment

import com.reevajs.reeva.core.lifecycle.ModuleRecord
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.ecmaAssert

open class DynamicDeclarativeEnvRecord(protected val realm: Realm, outer: EnvRecord?) : EnvRecord(outer) {
    private val bindings = mutableMapOf<String, Binding>()

    @ECMAImpl("9.1.1.1.1")
    override fun hasBinding(name: EnvRecordKey) = (name as String) in bindings

    @ECMAImpl("9.1.1.1.2")
    override fun createMutableBinding(name: EnvRecordKey, deletable: Boolean) {
        // 1. Assert: envRec does not already have a binding for N.
        ecmaAssert(!hasBinding(name))

        // 2. Create a mutable binding in envRec for N and record that it is uninitialized. If D is true, record that
        //    the newly created binding may be deleted by a subsequent DeleteBinding call.
        bindings[name as String] = Binding().apply { isDeletable = true }

        // 3. Return unused.
    }

    @ECMAImpl("9.1.1.1.3")
    override fun createImmutableBinding(name: EnvRecordKey, isStrict: Boolean) {
        // 1. Assert: envRec does not already have a binding for N.
        ecmaAssert(!hasBinding(name))

        // 2. Create an immutable binding in envRec for N and record that it is uninitialized. If S is true, record that
        //    the newly created binding is a strict binding.
        bindings[name as String] = Binding().apply { this.isStrict = isStrict }

        // 3. Return unused.
    }

    @ECMAImpl("9.1.1.1.4")
    override fun initializeBinding(name: EnvRecordKey, value: JSValue) {
        val binding = bindings[name as String]

        // 1. Assert: envRec must have an uninitialized binding for N.
        ecmaAssert(binding != null && binding.value == null)

        // 2. Set the bound value for N in envRec to V.
        binding.value = value

        // 3. Record that the binding for N in envRec has been initialized.
        // 4. Return unused.
    }

    @ECMAImpl("9.1.1.1.5")
    override fun setMutableBinding(name: EnvRecordKey, value: JSValue, isStrict: Boolean) {
        @Suppress("NAME_SHADOWING")
        var isStrict = isStrict
        val binding = bindings[name]

        // 1. If envRec does not have a binding for N, then
        if (binding == null) {
            // a. If S is true, throw a ReferenceError exception.
            if (isStrict)
                Errors.TODO("DeclarativeEnvRecord::setMutableBinding 1").throwReferenceError(realm)

            // b. Perform envRec.CreateMutableBinding(N, true).
            createMutableBinding(name, true)

            // c. Perform ! envRec.InitializeBinding(N, V).
            initializeBinding(name, value)

            // d. Return unused.
            return
        }

        // 2. If the binding for N in envRec is a strict binding, set S to true.
        if (binding.isStrict)
            isStrict = true

        // 3. If the binding for N in envRec has not yet been initialized, throw a ReferenceError exception.
        if (binding.value == null)
            Errors.TODO("DeclarativeEnvRecord::setMutableBinding 2").throwReferenceError(realm)

        // 4. Else if the binding for N in envRec is a mutable binding, change its bound value to V.
        if (!binding.isImmutable) {
            binding.value = value
        }
        // 5. Else,
        else {
            // a. Assert: This is an attempt to change the value of an immutable binding.
            // b. If S is true, throw a TypeError exception
            if (isStrict)
                Errors.TODO("DeclarativeEnvRecord::setMutableBinding 3").throwReferenceError(realm)
        }

        // 6. Return unused.
    }

    @ECMAImpl("9.1.1.1.6")
    override fun getBindingValue(name: EnvRecordKey, isStrict: Boolean): JSValue {
        val binding = bindings[name as String]

        // 1. Assert: envRec has a binding for N.
        ecmaAssert(binding != null)

        // 2. If the binding for N in envRec is an uninitialized binding, throw a Referenceerror exception.
        if (binding.value == null)
            Errors.TODO("DeclarativeEnvRecord::getBindingValue").throwReferenceError(realm)

        // 3. Return the value currently bound to N in envRec.
        return binding.value!!
    }

    @ECMAImpl("9.1.1.1.7")
    override fun deleteBinding(name: EnvRecordKey): Boolean {
        val binding = bindings[name as String]

        // 1. Assert: envRec has a binding for the name that is the value of N.
        ecmaAssert(binding != null)

        // 2. If the binding for N in envRec cannot be deleted, return false.
        if (!binding.isDeletable)
            return false

        // 3. Remove the binding for N from envRec.
        bindings.remove(name)

        // 4. Return true.
        return true
    }

    @ECMAImpl("9.1.1.1.8")
    override fun hasThisBinding(): Boolean {
        // 1. Return false
        return false
    }

    @ECMAImpl("9.1.1.1.9")
    override fun hasSuperBinding(): Boolean {
        // 1. Return false
        return false
    }

    @ECMAImpl("9.1.1.1.10")
    override fun withBaseObject(): JSObject? {
        // 1. Return undefined.
        return null
    }

    protected open class Binding private constructor(var value: JSValue?, private var flags: Int) {
        open val isInitialized: Boolean
            get() = value == null

        var isDeletable: Boolean
            get() = (flags and DELETABLE) != 0
            set(value) {
                flags = if (value) flags or DELETABLE else flags and DELETABLE.inv()
            }

        var isImmutable: Boolean
            get() = (flags and IMMUTABLE) != 0
            set(value) {
                flags = if (value) flags or IMMUTABLE else flags and IMMUTABLE.inv()
            }

        var isStrict: Boolean
            get() = (flags and STRICT) != 0
            set(value) {
                flags = if (value) flags or STRICT else flags and STRICT.inv()
            }

        constructor(value: JSValue? = null) : this(value, 0)

        companion object {
            private const val DELETABLE = 1 shl 0
            private const val IMMUTABLE = 1 shl 1
            private const val STRICT = 1 shl 2
        }
    }

    protected class IndirectBinding(
        val sourceName: String,
        val sourceModule: ModuleRecord,
    ) : Binding(null) {
        override val isInitialized: Boolean
            get() = true
    }
}
