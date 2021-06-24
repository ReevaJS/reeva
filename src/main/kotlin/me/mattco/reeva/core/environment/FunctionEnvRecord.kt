package me.mattco.reeva.core.environment

import me.mattco.reeva.core.Realm
import me.mattco.reeva.interpreter.Interpreter
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.ecmaAssert

class FunctionEnvRecord(
    realm: Realm,
    isStrict: Boolean,
    val functionObject: Interpreter.IRFunction,
) : DeclarativeEnvRecord(realm, isStrict) {
    private lateinit var thisValue: JSValue
    private lateinit var thisBindingStatus: BindingStatus
    private var newTarget: JSObject? = null

    fun bindThisValue(value: JSValue) {
        ecmaAssert(thisBindingStatus != BindingStatus.Lexical)
        if (thisBindingStatus == BindingStatus.Initialized)
            Errors.TODO("FunctionEnvRecord::bindThisValue").throwReferenceError(realm)
        thisValue = value
        thisBindingStatus = BindingStatus.Initialized
    }

    override fun hasThisBinding() = thisBindingStatus != BindingStatus.Lexical

    override fun hasSuperBinding(): Boolean {
        if (thisBindingStatus == BindingStatus.Lexical)
            return false

        // TODO: [[HomeObject]] check
        return false
    }

    fun getThisBinding(): JSValue {
        ecmaAssert(thisBindingStatus != BindingStatus.Lexical)
        if (thisBindingStatus == BindingStatus.Uninitialized)
            Errors.TODO("FunctionEnvRecord::getThisBinding").throwReferenceError(realm)
        return thisValue
    }

    fun getSuperBase(): JSValue {
        TODO()
    }

    enum class BindingStatus {
        Lexical,
        Initialized,
        Uninitialized,
    }
}
