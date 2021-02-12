package me.mattco.reeva.runtime.functions

import me.mattco.reeva.Reeva
import me.mattco.reeva.core.ExecutionContext
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSNull
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.ecmaAssert

abstract class JSFunction(
    realm: Realm,
    var isStrict: Boolean = false,
    prototype: JSValue = realm.functionProto,
) : JSObject(realm, prototype) {
    var isCallable: Boolean = true
    var isConstructable: Boolean = false

    abstract fun evaluate(arguments: JSArguments): JSValue

    open fun getNewThisValue(oldThis: JSValue): JSValue {
        return when {
            isStrict -> oldThis
            oldThis == JSUndefined || oldThis == JSNull -> realm.globalObject
            else -> Operations.toObject(oldThis)
        }
    }

    fun call(arguments: JSArguments): JSValue {
        val newThis = getNewThisValue(arguments.thisValue)
        return Reeva.activeAgent.withContext(ExecutionContext(this)) {
            evaluate(arguments.withThisValue(newThis))
        }
    }

    fun construct(arguments: JSArguments): JSValue {
        ecmaAssert(arguments.newTarget is JSObject)

        val thisValue = Operations.ordinaryCreateFromConstructor(
            arguments.newTarget,
            realm.objectProto,
        )

        val result = Reeva.activeAgent.withContext(ExecutionContext(this)) {
            evaluate(arguments.withThisValue(thisValue))
        }
        if (result is JSObject)
            return result

        return thisValue
    }

    enum class ThisMode {
        Lexical,
        NonLexical,
        Strict,
        Global
    }
}
