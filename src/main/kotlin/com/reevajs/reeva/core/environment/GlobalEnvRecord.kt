package com.reevajs.reeva.core.environment

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.ecmaAssert
import com.reevajs.reeva.utils.expect
import com.reevajs.reeva.utils.key

@ECMAImpl("9.1.1.4")
class GlobalEnvRecord(
    private val realm: Realm,
    private val objectRecord: ObjectEnvRecord,
    private val declarativeRecord: DeclarativeEnvRecord,
    val globalThisValue: JSValue,
) : EnvRecord(null) {
    private val varNames = mutableSetOf<String>()

    @ECMAImpl("9.1.1.4.1")
    override fun hasBinding(name: String): Boolean {
        // 1. Let DclRec be envRec.[[DeclarativeRecord]]
        // 2. If ! DclRec.HasBinding(N) is true, return true.
        if (declarativeRecord.hasBinding(name))
            return true

        // 3. Let ObjRec be envRec.[[ObjectRecord]].
        // 4. Return ? ObjRec.HasBinding(N).
        return objectRecord.hasBinding(name)
    }

    @ECMAImpl("9.1.1.4.2")
    override fun createMutableBinding(name: String, deletable: Boolean) {
        // 1. Let DclRec be envRec.[[DeclarativeRecord]].
        // 2. If ! DclRec.HasBinding(N) is true, throw a TypeError exception.
        if (declarativeRecord.hasBinding(name))
            Errors.TODO("GlobalEnvRecord::createMutableBinding").throwTypeError(realm)

        // 3. Return DclRec.CreateMutableBinding(N, D)
        declarativeRecord.createMutableBinding(name, deletable)
    }

    @ECMAImpl("9.1.1.4.3")
    override fun createImmutableBinding(name: String, isStrict: Boolean) {
        // 1. Let DclRec be envRec.[[DeclarativeRecord]].
        // 2. If ! DclRec.HasBinding(N) is true, throw a TypeError exception.
        if (declarativeRecord.hasBinding(name))
            Errors.TODO("GlobalEnvRecord::createImmutableBinding").throwTypeError(realm)

        // 3. Return DclRec.CreateImmutableBinding(name, isStrict)
        return declarativeRecord.createImmutableBinding(name, isStrict)
    }

    @ECMAImpl("9.1.1.4.4")
    override fun initializeBinding(name: String, value: JSValue) {
        // 1. Let DclRec be envRec.[[DeclarativeRecord]].
        // 2. If ! DclRec.HasBinding(N) is true, then
        if (declarativeRecord.hasBinding(name)) {
            // a. Return ! DclRec.InitializeBinding(N, V).
            return declarativeRecord.initializeBinding(name, value)
        }

        // 3. Assert: If the binding exists, it must be in the object Environment Record.
        // TODO: Is this saying it must exist?
        ecmaAssert(objectRecord.hasBinding(name))

        // 4. Let ObjRec be envRec.[[ObjectRecord]].
        // 5. Return ? ObjRec.InitializeBinding(N, V).
        return objectRecord.initializeBinding(name, value)
    }

    @ECMAImpl("9.1.1.4.5")
    override fun setMutableBinding(name: String, value: JSValue, isStrict: Boolean) {
        // 1. Let DclRec be envRec.[[DeclarativeRecord]].
        // 2. If ! DclRec.HasBinding(N) is true, then
        if (declarativeRecord.hasBinding(name)) {
            // a. Return ! DclRec.SetMutableBinding(N, V, S).
            return declarativeRecord.setMutableBinding(name, value, isStrict)
        }

        // 3. Let ObjRec be envRec.[[ObjectRecord]].
        // 4. Return ? ObjRec.SetMutableBinding(N, V, S).
        return objectRecord.setMutableBinding(name, value, isStrict)
    }

    @ECMAImpl("9.1.1.4.6")
    override fun getBindingValue(name: String, isStrict: Boolean): JSValue {
        // 1. Let DclRec be envRec.[[DeclarativeRecord]].
        // 2. If ! DclRec.HasBinding(N) is true, then
        if (declarativeRecord.hasBinding(name)) {
            // a. Return DclRec.GetBindingValue(N, S).
            return declarativeRecord.getBindingValue(name, isStrict)
        }

        // 3. Let ObjRec be envRec.[[ObjectRecord]].
        // 4. Return ? ObjRec.GetBindingValue(N, S).
        return objectRecord.getBindingValue(name, isStrict)
    }

    @ECMAImpl("9.1.1.4.7")
    override fun deleteBinding(name: String): Boolean {
        // 1. Let DclRec be envRec.[[DeclarativeRecord]].
        // 2. If ! DclRec.HasBinding(N) is true, then
        if (declarativeRecord.hasBinding(name)) {
            // a. Return ! DclRec.DeleteBinding(N).
            return declarativeRecord.deleteBinding(name)
        }

        // 3. Let ObjRec be envRec.[[ObjectRecord]].
        // 4. Let globalObject be ObjRec.[[BindingObject]].
        // 5. Let existingProp be ? HasOwnProperty(globalObject, N).
        // 6. If existingProp is true, then
        if (objectRecord.bindingObject.hasProperty(name)) {
            // a. Let status be ? ObjRec.DeleteBinding(N).
            val status = objectRecord.deleteBinding(name)

            // b. If status is true, then
            if (status) {
                // i.  Let varNames be envRec.[[VarNames]].
                // ii. If N is an element of varNames, remove that element from the varNames.
                varNames.remove(name)
            }

            // c. Return status.
            return status
        }

        // 7. Return true.
        return true
    }

    @ECMAImpl("9.1.1.4.8")
    override fun hasThisBinding(): Boolean {
        // 1. Return true.
        return true
    }

    @ECMAImpl("9.1.1.4.9")
    override fun hasSuperBinding(): Boolean {
        // 1. Return false.
        return false
    }

    @ECMAImpl("9.1.1.4.10")
    override fun withBaseObject(): JSObject? {
        // 1. Return undefined
        return null
    }

    @ECMAImpl("9.1.1.4.11")
    fun getThisBinding(): JSValue {
        // 1. Return envRecord.[[GlobalThisValue]]
        return globalThisValue
    }

    @ECMAImpl("9.1.1.4.12")
    fun hasVarDeclaration(name: String): Boolean {
        // 1. Let varDeclaredNames be envRec.[[VarNames]].
        // 2. If varDeclaredNames contains N, return true.
        // 3. Return false.
        return name in varNames
    }

    @ECMAImpl("9.1.1.4.13")
    fun hasLexicalDeclaration(name: String): Boolean {
        // 1. Let DclRec be envRec.[[DeclarativeRecord]].
        // 2. Return ! DclRec.HasBinding(N).
        return declarativeRecord.hasBinding(name)
    }

    @ECMAImpl("9.1.1.4.14")
    fun hasRestrictedGlobalProperty(name: String): Boolean {
        // 1. Let ObjRec be envRec.[[ObjectRecord]].
        // 2. Let globalObject be ObjRec.[[BindingObject]].
        // 3. Let existingProp be ? globalObject.[[GetOwnProperty]](N).
        // 4. If existingProp is undefined, return false.
        val existingProp = objectRecord.bindingObject.getOwnPropertyDescriptor(name) ?: return false

        // 5. If existingProp.[[Configurable]] is true, return false.
        // 6. Return true.
        return !existingProp.isConfigurable
    }

    @ECMAImpl("9.1.1.4.15")
    fun canDeclareGlobalVar(name: String): Boolean {
        // 1. Let ObjRec be envRec.[[ObjectRecord]].
        // 2. Let globalObject be ObjRec.[[BindingObject]].
        val globalObject = objectRecord.bindingObject

        // 3. Let hasProperty be ? HasOwnProperty(globalObject, N).
        // 4. If hasProperty is true, return true.
        if (Operations.hasOwnProperty(globalObject, name.key()))
            return true

        // 5. Return ? IsExtensible(globalObject).
        return globalObject.isExtensible()
    }

    @ECMAImpl("9.1.1.4.16")
    fun canDeclareGlobalFunction(name: String): Boolean {
        // 1. Let ObjRec be envRec.[[ObjectRecord]].
        // 2. Let globalObject be ObjRec.[[BindingObject]].
        val globalObject = objectRecord.bindingObject

        // 3. Let existingProp be ? globalObject.[[GetOwnProperty]](N).
        // 4. If existingProp is undefined, return ? IsExtensible(globalObject).
        val existingProp = globalObject.getOwnPropertyDescriptor(name) ?: return globalObject.isExtensible()

        // 5. If existingProp.[[Configurable]] is true, return true.
        if (existingProp.isConfigurable)
            return true

        // 6. If IsDataDescriptor(existingProp) is true and existingProp has attribute values { [[Writable]]: true, [[Enumerable]]: true }, return true.
        // 7. Return false.
        return existingProp.isDataDescriptor && existingProp.isWritable && existingProp.isEnumerable
    }

    @ECMAImpl("9.1.1.4.17")
    fun createGlobalVarBinding(name: String, deletable: Boolean) {
        // 1. Let ObjRec be envRec.[[ObjectRecord]].
        // 2. Let globalObject be ObjRec.[[BindingObject]].
        val globalObject = objectRecord.bindingObject

        // 3. Let hasProperty be ? HasOwnProperty(globalObject, N).
        // 4. Let extensible be ? IsExtensible(globalObject).
        // 5. If hasProperty is false and extensible is true, then
        if (!Operations.hasOwnProperty(globalObject, name.key()) && globalObject.isExtensible()) {
            // a. Perform ? ObjRec.CreateMutableBinding(N, D).
            objectRecord.createMutableBinding(name, deletable)

            // b. Perform ? ObjRec.InitializeBinding(N, undefined).
            objectRecord.initializeBinding(name, JSUndefined)
        }

        // 6. Let varDeclaredNames be envRec.[[VarNames]].
        // 7. If varDeclaredNames does not contain N, then
        //    a. Append N to varDeclaredNames.
        // Note: There is no need for a check here, as varNames in a set instead of a list
        varNames.add(name)

        // 8. Return unused.
    }

    @ECMAImpl("9.1.1.4.18")
    fun createGlobalFunctionBinding(name: String, value: JSValue, deletable: Boolean) {
        // 1. Let ObjRec be envRec.[[ObjectRecord]].
        // 2. Let globalObject be ObjRec.[[BindingObject]].
        val globalObject = objectRecord.bindingObject

        // 3. Let existingProp be ? globalObject.[[GetOwnProperty]](N).
        val existingProp = globalObject.getOwnPropertyDescriptor(name)

        // 4. If existingProp is undefined or existingProp.[[Configurable]] is true, then
        val desc = if (existingProp == null || existingProp.isConfigurable) {
            // a. Let desc be the PropertyDescriptor { [[Value]]: V, [[Writable]]: true, [[Enumerable]]: true, [[Configurable]]: D }.
            Descriptor(value).apply {
                setWritable(true)
                setEnumerable(true)
                setConfigurable(deletable)
            }
        }
        // 5. Else,
        else {
            // a. Let desc be the PropertyDescriptor { [[Value]]: V }.
            Descriptor(value)
        }

        // 6. Perform ? DefinePropertyOrThrow(globalObject, N, desc).
        Operations.definePropertyOrThrow(globalObject, name.key(), desc)

        // 7. Perform ? Set(globalObject, N, V, false).
        Operations.set(globalObject, name.key(), value, false)

        // 8. Let varDeclaredNames be envRec.[[VarNames]].
        // 9. If varDeclaredNames does not contain N, then
        // a. Append N to varDeclaredNames.
        // Note: There is no need for a check here, as varNames in a set instead of a list
        varNames.add(name)

        // 10. Return unused.
    }
}
