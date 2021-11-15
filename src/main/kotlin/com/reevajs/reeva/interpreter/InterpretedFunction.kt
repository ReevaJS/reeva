package com.reevajs.reeva.interpreter

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.ExecutionContext
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.core.environment.EnvRecord
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.functions.generators.JSGeneratorObject
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.transformer.TransformedSource
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.ecmaAssert

abstract class InterpretedFunction(
    realm: Realm,
    val transformedSource: TransformedSource,
    val outerEnvRecord: EnvRecord,
    prototype: JSValue = realm.functionProto,
) : JSFunction(
    realm,
    transformedSource.functionInfo.name,
    transformedSource.functionInfo.isStrict,
    prototype,
) {
    /**
     * We don't need to accept a newTarget or push any environments, as this is
     * handled by the interpreter (the function may not need an environment
     * record at all).
     */
    @ECMAImpl("10.2.1.1")
    protected fun prepareForOrdinaryCall(): ExecutionContext {
        val agent = Agent.activeAgent
        val callerContext = agent.runningExecutionContext

        val calleeContext = ExecutionContext(
            this,
            realm,
            outerEnvRecord,
            callerContext.executable,
            null
        )

        agent.pushExecutionContext(calleeContext)
        return calleeContext
    }

    @ECMAImpl("10.2.1.2")
    protected fun ordinaryCallBindThis(calleeContext: ExecutionContext, thisArgument: JSValue): JSValue {
        // TODO: This could not be further from the spec. We need to associate
        //       a ThisMode with each function so we can implement this properly
        return thisArgument
    }
}

class NormalInterpretedFunction private constructor(
    realm: Realm,
    transformedSource: TransformedSource,
    outerEnvRecord: EnvRecord,
) : InterpretedFunction(realm, transformedSource, outerEnvRecord) {
    override fun evaluate(arguments: JSArguments): JSValue {
        val args = listOf(arguments.thisValue, arguments.newTarget) + arguments
        val result = Interpreter(transformedSource, args, outerEnvRecord).interpret()
        return result.valueOrElse { throw result.error() }
    }

    @ECMAImpl("10.2.1", "[[Call]]")
    override fun call(arguments: JSArguments): JSValue {
        // 1.  Let callerContext be the running execution context.
        val agent = Agent.activeAgent
        val callerContext = agent.runningExecutionContext

        // 2.  Let calleeContext be PrepareForOrdinaryCall(F, undefined).
        val calleeContext = prepareForOrdinaryCall()

        // 3.  Assert: calleeContext is now the running execution context.
        ecmaAssert(calleeContext == agent.runningExecutionContext)

        // 4.  If F.[[IsClassConstructor]] is true, then
        if (isClassConstructor) {
            // a.  Let error be a newly created TypeError object.
            // b.  NOTE: error is created in calleeContext with F's associated Realm Record.
            // c.  Remove calleeContext from the execution context stack and restore callerContext as the running
            //     execution context.
            // d.  Return ThrowCompletion(error).

            agent.popExecutionContext()
            Errors.Class.CtorRequiresNew.throwTypeError(calleeContext.realm)
        }

        // 5.  Perform OrdinaryCallBindThis(F, calleeContext, thisArgument).
        // Note: This return value is non-spec. The spec simply binds it to the current environment, so it doesn't need
        //       to return it. We need to pass it into the interpreter manually.
        val thisArgument = ordinaryCallBindThis(calleeContext, arguments.thisValue)

        return try {
            // 6.  Let result be OrdinaryCallEvaluateBody(F, argumentsList).
            // 8.  If result.[[Type]] is return, return NormalCompletion(result.[[Value]]).
            // 9.  ReturnIfAbrupt(result).
            // 10. Return NormalCompletion(undefined).
            evaluate(arguments.withThisValue(thisArgument))
        } finally {
            // 7.  Remove calleeContext from the execution context stack and restore callerContext as the running
            //     execution context.
            agent.popExecutionContext()
        }
    }

    @ECMAImpl("10.2.2", "[[Construct]]")
    override fun construct(arguments: JSArguments): JSValue {
        // 1.  Let callerContext be the running executionContext.
        val agent = Agent.activeAgent
        val callerContext = agent.runningExecutionContext

        // 2.  Let kind be F.[[ConstructorKind]].
        // 3.  If kind is base, then
        var thisArgument = if (constructorKind == ConstructorKind.Base) {
            // a. Let thisArgument be ? OrdinaryCreateFromConstructor(newTarget, "%Object.prototype%").
            Operations.ordinaryCreateFromConstructor(
                arguments.newTarget,
                defaultProto = Realm::objectProto,
            )
        } else arguments.thisValue

        // 4.  Let calleeContext be PrepareForOrdinaryCall(F, newTarget).
        val calleeContext = prepareForOrdinaryCall()

        // 5.  Assert: calleeContext is now the running execution context.
        ecmaAssert(calleeContext == agent.runningExecutionContext)

        //  6.  If kind is base, then
        if (constructorKind == ConstructorKind.Base) {
            // a. Perform OrdinaryCallBindThis(F, calleeContext, thisArgument).
            // Note: See call for explanation of return value
            thisArgument = ordinaryCallBindThis(calleeContext, thisArgument)

            // b.  Let initializeResult be InitializeInstanceElements(thisArgument, F).
            // c.  If initializeResult is an abrupt completion, then
            //     i.  Remove calleeContext from the execution context stack and restore callerContext as the running
            //         execution context.
            //     ii. Return Completion(initializeResult).

            // Class fields are handled in the IR/bytecode, so no work is done here
        }

        // 7.  Let constructorEnv be the LexicalEnvironment of calleeContext.
        // Note: While our ExecutionContexts do store EnvRecords, EnvRecords do not store receiver bindings.

        return try {
            // 8.  Let result be OrdinaryCallEvaluateBody(F, argumentsList).
            val result = evaluate(arguments.withThisValue(thisArgument))


            // 10. If result.[[Type]] is return, then
            when {
                // a.  If Type(result.[[Value]]) is Object, return NormalCompletion(result.[[Value]]).
                result is JSObject -> result
                // b.  If kind is base, return NormalCompletion(thisArgument).
                constructorKind == ConstructorKind.Base -> thisArgument
                // c. If result.[[Value]] is not undefined, throw a TypeError exception.
                // Note: We have to use the callerContext realm here, as in the spec the ExecutionContext has been
                //       removed at this point, but here we do not remove the context until after this try-finally block
                //       executes.
                result != JSUndefined -> Errors.Class.ReturnObjectFromDerivedCtor.throwTypeError(callerContext.realm)
                else -> {
                    // 11. Else, ReturnIfAbrupt(result).
                    // Note: This is implicit, as we don't have a catch block. Any exceptions will propagate upwards.

                    // 12. Return ? constructorEnv.getThisBinding().
                    thisArgument
                }
            }

        } finally {
            // 9.  Remove calleeContext from the execution context stack and restore callerContext as the running
            //     execution context.
            agent.popExecutionContext()
        }
    }

    companion object {
        fun create(
            transformedSource: TransformedSource,
            outerEnvRecord: EnvRecord,
            realm: Realm = Agent.activeAgent.getActiveRealm(),
        ) = NormalInterpretedFunction(realm, transformedSource, outerEnvRecord).initialize()
    }
}

class GeneratorInterpretedFunction private constructor(
    realm: Realm,
    transformedSource: TransformedSource,
    outerEnvRecord: EnvRecord,
) : InterpretedFunction(realm, transformedSource, outerEnvRecord) {
    private lateinit var generatorObject: JSGeneratorObject

    override fun init() {
        super.init()
        defineOwnProperty("prototype", realm.functionProto)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        if (!::generatorObject.isInitialized) {
            generatorObject = JSGeneratorObject.create(
                transformedSource,
                arguments.thisValue,
                arguments,
                Interpreter.GeneratorState(),
                outerEnvRecord,
            )
        }

        return generatorObject
    }

    companion object {
        fun create(
            transformedSource: TransformedSource,
            outerEnvRecord: EnvRecord,
            realm: Realm = Agent.activeAgent.getActiveRealm(),
        ) = GeneratorInterpretedFunction(realm, transformedSource, outerEnvRecord).initialize()
    }
}
