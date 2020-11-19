package me.mattco.reeva.runtime.functions

import me.mattco.reeva.core.Agent
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ReturnException
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.core.environment.FunctionEnvRecord
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.*

/**
 * A function declared in a JS script context. Created by
 * the interpreter.
 */
abstract class JSInterpreterFunction @JvmOverloads constructor(
    realm: Realm,
    thisMode: ThisMode,
    envRecord: EnvRecord?,
    isStrict: Boolean,
    homeObject: JSValue,
    internal val sourceText: String,
    prototype: JSObject = realm.functionProto,
) : JSFunction(
    realm,
    thisMode,
    envRecord,
    homeObject,
    isStrict,
    prototype
) {
    override fun call(thisValue: JSValue, arguments: JSArguments): JSValue {
        return try {
            super.call(thisValue, arguments)
            JSUndefined
        } catch (e: ReturnException) {
            e.value
        }
    }

    override fun construct(arguments: JSArguments, newTarget: JSValue): JSValue {
        // TODO: Reduce code duplication
        ecmaAssert(newTarget is JSObject)

        val thisArgument = if (constructorKind == ConstructorKind.Base) {
            Operations.ordinaryCreateFromConstructor(newTarget, realm.objectProto)
        } else null

        val calleeContext = Operations.prepareForOrdinaryCall(this, newTarget)
        ecmaAssert(Agent.runningContext == calleeContext)
        if (constructorKind == ConstructorKind.Base) {
            Operations.ordinaryCallBindThis(this, calleeContext, thisArgument!!)
            try {
                initializeInstanceFields(thisArgument, this)
            } catch (e: ThrowException) {
                Agent.popContext()
                throw e
            }
        }
        val constructorEnv = calleeContext.lexicalEnv
        expect(constructorEnv is FunctionEnvRecord)

        try {
            evaluate(arguments)
        } catch (e: ReturnException) {
            if (e.value is JSObject)
                return e.value
            if (constructorKind == ConstructorKind.Base)
                return thisArgument!!
            if (e.value != JSUndefined)
                Errors.Class.ReturnObjectFromDerivedCtor.throwTypeError()
        } finally {
            Agent.popContext()
        }

        return constructorEnv.getThisBinding()
    }
}
