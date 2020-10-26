package me.mattco.reeva.runtime.values.functions

import me.mattco.reeva.ast.FormalParametersNode
import me.mattco.reeva.ast.FunctionStatementList
import me.mattco.reeva.compiler.Completion
import me.mattco.reeva.runtime.Agent
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.environment.EnvRecord
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.errors.JSErrorObject
import me.mattco.reeva.runtime.values.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.ecmaAssert
import me.mattco.reeva.utils.throwError

class JSInterpretedFunction(
    realm: Realm,
    thisMode: ThisMode,
    envRecord: EnvRecord?,
    isStrict: Boolean,
    isClassConstructor: Boolean,
    homeObject: JSValue,
    prototype: JSObject = realm.functionProto,
    internal val sourceText: String,
    private val evalBody: (JSInterpretedFunction, JSArguments) -> Completion,
) : JSFunction(
    realm,
    thisMode,
    envRecord,
    homeObject,
    isClassConstructor,
    isStrict,
    prototype
) {

    override fun call(thisValue: JSValue, arguments: JSArguments): JSValue {
        if (isClassConstructor) {
            throwError<JSTypeErrorObject>("TODO: message")
            return INVALID_VALUE
        }
        val callerContext = Agent.runningContext
        val calleeContext = Operations.prepareForOrdinaryCall(this, JSUndefined)
        if (Agent.hasError())
            return INVALID_VALUE
        ecmaAssert(Agent.runningContext == calleeContext)
        Operations.ordinaryCallBindThis(this, calleeContext, thisValue)
        val result = evalBody(this, arguments)
        Agent.popContext()
        if (result.isReturn)
            return result.value
        if (result.isAbrupt) {
            Agent.throwError(result.value as JSErrorObject)
            return INVALID_VALUE
        }
        return JSUndefined
    }

    override fun construct(arguments: JSArguments, newTarget: JSValue): JSValue {
        TODO("Not yet implemented")
    }
}
