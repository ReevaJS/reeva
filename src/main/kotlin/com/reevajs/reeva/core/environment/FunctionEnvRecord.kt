package com.reevajs.reeva.core.environment

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.ecmaAssert

class FunctionEnvRecord(
    realm: Realm,
    outer: EnvRecord?,
    private val functionObject: JSFunction,
    val newTarget: JSValue,
) : DeclarativeEnvRecord(realm, outer) {
    private var thisValue: JSValue = JSUndefined
    private var thisBindingStatus = if (functionObject.thisMode == JSFunction.ThisMode.Lexical) {
        ThisBindingStatus.Lexical
    } else ThisBindingStatus.Uninitialized

    @ECMAImpl("9.1.1.3.1")
    fun bindThisValue(value: JSValue) {
        // 1. Assert: envRec.[[ThisBindingStatus]] is not lexical.
        ecmaAssert(thisBindingStatus != ThisBindingStatus.Lexical)

        // 2. If envRec.[[ThisBindingStatus]] is initialized, throw a ReferenceError exception.
        if (thisBindingStatus == ThisBindingStatus.Initialized)
            Errors.TODO("FunctionEnvRecord::bindThisValue").throwReferenceError(realm)

        // 3. Set envRec.[[ThisValue]] to V.
        thisValue = value

        // 4. Set envRec.[[ThisBindingStatus]] to initialized.
        thisBindingStatus = ThisBindingStatus.Initialized

        // 5. Return V.
    }

    @ECMAImpl("9.1.1.3.2")
    override fun hasThisBinding(): Boolean {
        // 1. If envRec.[[ThisBindingStatus]] is lexical, return false; otherwise, return true.
        return thisBindingStatus != ThisBindingStatus.Lexical
    }

    @ECMAImpl("9.1.1.3.3")
    override fun hasSuperBinding(): Boolean {
        // 1. If envRec.[[ThisBindingStatus]] is lexical, return false.
        if (thisBindingStatus == ThisBindingStatus.Lexical)
            return false

        // 2. If envRec.[[FunctionObject]].[[HomeObject]] is undefined, return false; otherwise, return true.
        return functionObject.homeObject != JSUndefined
    }

    @ECMAImpl("9.1.1.3.4")
    override fun getThisBinding(): JSValue {
        // 1. Assert: envRec.[[ThisBindingStatus]] is not lexical.
        ecmaAssert(thisBindingStatus != ThisBindingStatus.Lexical)

        // 2. If envRec.[[ThisBindingStatus]] is uninitialized, throw a ReferenceError exception.
        if (thisBindingStatus == ThisBindingStatus.Uninitialized)
            Errors.TODO("FunctionEnvRecord::getThisBinding").throwReferenceError(realm)

        // 3. Return envRec.[[ThisValue]].
        return thisValue
    }

    @ECMAImpl("9.1.1.3.5")
    fun getSuperBase(): JSValue {
        // 1. Let home be envRec.[[FunctionObject]].[[HomeObject]].
        val home = functionObject.homeObject

        // 2. If home is undefined, return undefined.
        if (home == JSUndefined)
            return JSUndefined

        // 3. Assert: Type(home) is Object.
        ecmaAssert(home is JSObject)

        // 4. Return ? home.[[GetPrototypeOf]]().
        return home.getPrototype()
    }

    enum class ThisBindingStatus {
        Lexical,
        Initialized,
        Uninitialized,
    }
}
