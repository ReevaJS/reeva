package me.mattco.reeva.runtime.functions

import me.mattco.reeva.core.Agent
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ReturnException
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.core.environment.FunctionEnvRecord
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.ecmaAssert
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.throwTypeError

class JSInterpretedFunction(
    realm: Realm,
    thisMode: ThisMode,
    envRecord: EnvRecord?,
    isStrict: Boolean,
    homeObject: JSValue,
    prototype: JSObject = realm.functionProto,
    internal val sourceText: String,
    private val evalBody: (JSInterpretedFunction, JSArguments) -> JSValue,
) : JSFunction(
    realm,
    thisMode,
    envRecord,
    homeObject,
    isStrict,
    prototype
) {
    override fun call(thisValue: JSValue, arguments: JSArguments): JSValue {
        if (isClassConstructor)
            throwTypeError("TODO: message")
        val calleeContext = Operations.prepareForOrdinaryCall(this, JSUndefined)
        ecmaAssert(Agent.runningContext == calleeContext)
        Operations.ordinaryCallBindThis(this, calleeContext, thisValue)
        try {
            evalBody(this, arguments)
        } catch (e: ReturnException) {
            return e.value
        } finally {
            Agent.popContext()
        }
        return JSUndefined
    }

    override fun construct(arguments: JSArguments, newTarget: JSValue): JSValue {
        ecmaAssert(newTarget is JSObject)

        val thisArgument = if (constructorKind == ConstructorKind.Base) {
            Operations.ordinaryCreateFromConstructor(newTarget, realm.objectProto)
        } else null

        val calleeContext = Operations.prepareForOrdinaryCall(this, newTarget)
        ecmaAssert(Agent.runningContext == calleeContext)
        if (constructorKind == ConstructorKind.Base)
            Operations.ordinaryCallBindThis(this, calleeContext, thisArgument!!)
        val constructorEnv = calleeContext.lexicalEnv
        expect(constructorEnv is FunctionEnvRecord)
        try {
            evalBody(this, arguments)
        } catch (e: ReturnException) {
            if (e.value is JSObject)
                return e.value
            if (constructorKind == ConstructorKind.Base)
                return thisArgument!!
            if (e.value != JSUndefined)
                throwTypeError("TODO: message")
        } finally {
            Agent.popContext()
        }
        return constructorEnv.getThisBinding()

    }
}
