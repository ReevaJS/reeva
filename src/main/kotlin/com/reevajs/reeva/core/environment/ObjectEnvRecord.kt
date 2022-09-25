package com.reevajs.reeva.core.environment

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.toBoolean
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.expect
import com.reevajs.reeva.utils.key
import com.reevajs.reeva.utils.unreachable

@ECMAImpl("9.1.1.2")
class ObjectEnvRecord(
    private val realm: Realm,
    val bindingObject: JSObject,
    private val isWithEnvironment: Boolean,
    outer: EnvRecord?,
) : EnvRecord(outer) {
    @ECMAImpl("9.1.1.2.1")
    override fun hasBinding(name: String): Boolean {
        // 1. Let bindingObject be envRec.[[BindingObject]].
        // 2. let foundBinding be ? HasProperty(bindingObject, N).
        // 3. If foundBinding is false, return false.
        if (!bindingObject.hasProperty(name.key()))
            return false

        // 4. If envRec.[[IsWithEnvironment]] is false, return true.
        if (!isWithEnvironment)
            return true

        // 5. Let unscopables be ? Get(bindingObject, @@unscopables).
        val unscopables = bindingObject.get(Realm.WellKnownSymbols.unscopables)

        // 6. If Type(unscopables) is Object, then
        if (unscopables is JSObject) {
            // a. Let blocked be ToBoolean(? Get(unscopables, N)).
            // b. If blocked is true, return false.
            if (unscopables.get(name).toBoolean())
                return false
        }

        // 7. Return true.
        return true
    }

    @ECMAImpl("9.1.1.2.2")
    override fun createMutableBinding(name: String, deletable: Boolean) {
        // 1. Let bindingObject be envRec.[[BindingObject]]
        // 2. Perform ? DefinePropertyOrThrow(bindingObject, N, PropertyDescriptor { [[Value]]: undefined, [[Writable]]:
        //    true, [[Enumerable]]: true, [[Configurable]]: D }).
        AOs.definePropertyOrThrow(bindingObject, name.key(), Descriptor(JSUndefined, 0).apply {
            setWritable(true)
            setEnumerable(true)
            setConfigurable(deletable)
        })

        // 3. Return unused.
    }

    @ECMAImpl("9.1.1.2.3")
    override fun createImmutableBinding(name: String, shouldThrowOnReassignment: Boolean) {
        // The CreateImmutableBinding concrete method of an object Environment Record is never used within this
        // specification
        unreachable()
    }

    @ECMAImpl("9.1.1.2.4")
    override fun initializeBinding(name: String, value: JSValue) {
        // 1. Perform ? envRec.SetMutableBinding(N, V, false).
        setMutableBinding(name, value, false)

        // 2. Return ununsed.
    }

    @ECMAImpl("9.1.1.2.5")
    override fun setMutableBinding(name: String, value: JSValue, isStrict: Boolean) {
        // 1. Let bindingObject be envRec.[[BindingObject]]
        // 2. Let stillExists be ? HasProperty(bindingObject, N).
        // 3. If stillExists is false and S is true, throw a ReferenceError exception.
        if (!bindingObject.hasProperty(name) && isStrict)
            Errors.TODO("ObjectEnvironmentRecord::setMutableBinding").throwReferenceError(realm)

        // 4. Perform ? Set(bindingObject, N, V, S)
        AOs.set(bindingObject, name.key(), value, isStrict)

        // 5. Return unused.
    }

    @ECMAImpl("9.1.1.2.6")
    override fun getBindingValue(name: String, isStrict: Boolean): JSValue {
        // 1. Let bindingObject be envRec.[[BindingObject]]
        // 2. Let value be ? HasProperty(bindingObject, N).
        // 3. If value is false, then
        if (!bindingObject.hasProperty(name)) {
            // a. If S is false, return undefined; otherwise throw a ReferenceError exception.
            if (!isStrict)
                return JSUndefined

            Errors.TODO("ObjectEnvironmentRecord::getBindingValue").throwReferenceError(realm)
        }

        // 4. Return ? Get(bindingObject, N)
        return bindingObject.get(name.key())
    }

    @ECMAImpl("9.1.1.2.7")
    override fun deleteBinding(name: String): Boolean {
        // 1. Let bindingObject be envRec.[[BindingObject]].
        // 2. Return ? bindingObject.[[Delete]](N).
        return bindingObject.delete(name)
    }

    @ECMAImpl("9.1.1.2.8")
    override fun hasThisBinding(): Boolean {
        // 1. Return false
        return false
    }

    @ECMAImpl("9.1.1.2.9")
    override fun hasSuperBinding(): Boolean {
        // 1. Return false
        return false
    }

    @ECMAImpl("9.1.1.2.10")
    override fun withBaseObject(): JSObject? {
        // 1. If envRec.[[IsWithEnvironment]] is true, return envRec.[[BindingObject]].
        if (isWithEnvironment)
            return bindingObject

        // 2. Otherwise, return undefined.
        return null
    }
}
