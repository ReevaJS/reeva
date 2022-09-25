package com.reevajs.reeva.interpreter

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.ExecutionContext
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.environment.FunctionEnvRecord
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.functions.generators.JSGeneratorObject
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.toObject
import com.reevajs.reeva.transformer.TransformedSource
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.ecmaAssert

abstract class InterpretedFunction(
    realm: Realm,
    val transformedSource: TransformedSource,
    prototype: JSValue = realm.functionProto,
) : JSFunction(
    realm,
    transformedSource.functionInfo.name,
    when {
        transformedSource.functionInfo.isArrow -> ThisMode.Lexical
        transformedSource.functionInfo.isStrict -> ThisMode.Strict
        else -> ThisMode.Global
    },
    transformedSource.functionInfo.isStrict,
    prototype,
) {
    private val environment = Agent.activeAgent.activeEnvRecord

    protected abstract fun evaluate(arguments: JSArguments): JSValue

    @ECMAImpl("10.2.1", "[[Call]]")
    override fun call(arguments: JSArguments): JSValue {
        // 1.  Let callerContext be the running execution context.
        val agent = Agent.activeAgent
        val callerContext = agent.runningExecutionContext

        // 2.  Let calleeContext be PrepareForOrdinaryCall(F, undefined).
        val calleeContext = prepareForOrdinaryCall(JSUndefined)

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
        ordinaryCallBindThis(calleeContext, arguments.thisValue)

        return try {
            // 6.  Let result be OrdinaryCallEvaluateBody(F, argumentsList).
            // 8.  If result.[[Type]] is return, return NormalCompletion(result.[[Value]]).
            // 9.  ReturnIfAbrupt(result).
            // 10. Return NormalCompletion(undefined).
            evaluate(arguments)
        } finally {
            // 7.  Remove calleeContext from the execution context stack and restore callerContext as the running
            //     execution context.
            agent.popExecutionContext()
            ecmaAssert(agent.runningExecutionContext == callerContext)
        }
    }

    @ECMAImpl("10.2.2", "[[Construct]]")
    override fun construct(arguments: JSArguments): JSValue {
        // 1.  Let callerContext be the running executionContext.
        val agent = Agent.activeAgent
        val callerContext = agent.runningExecutionContext

        // 2.  Let kind be F.[[ConstructorKind]].
        // 3.  If kind is base, then
        var thisArgument = if (constructorKind == JSFunction.ConstructorKind.Base) {
            // a. Let thisArgument be ? OrdinaryCreateFromConstructor(newTarget, "%Object.prototype%").
            Operations.ordinaryCreateFromConstructor(
                arguments.newTarget,
                defaultProto = Realm::objectProto,
            )
        } else arguments.thisValue

        // 4.  Let calleeContext be PrepareForOrdinaryCall(F, newTarget).
        val calleeContext = prepareForOrdinaryCall(arguments.newTarget)

        // 5.  Assert: calleeContext is now the running execution context.
        ecmaAssert(calleeContext == agent.runningExecutionContext)

        //  6.  If kind is base, then
        if (constructorKind == ConstructorKind.Base) {
            // a. Perform OrdinaryCallBindThis(F, calleeContext, thisArgument).
            ordinaryCallBindThis(calleeContext, thisArgument)

            // b.  Let initializeResult be InitializeInstanceElements(thisArgument, F).
            // c.  If initializeResult is an abrupt completion, then
            //     i.  Remove calleeContext from the execution context stack and restore callerContext as the running
            //         execution context.
            //     ii. Return Completion(initializeResult).

            // Class fields are handled in the IR/bytecode, so no work is done here
        }

        // 7.  Let constructorEnv be the LexicalEnvironment of calleeContext.
        val constructorEnv = calleeContext.envRecord as FunctionEnvRecord

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
                    constructorEnv.getThisBinding()
                }
            }
        } finally {
            // 9.  Remove calleeContext from the execution context stack and restore callerContext as the running
            //     execution context.
            agent.popExecutionContext()
        }
    }

    @ECMAImpl("10.2.1.1")
    protected fun prepareForOrdinaryCall(newTarget: JSValue): ExecutionContext {
        val agent = Agent.activeAgent

        // 1. Let callerContext be the running execution context.
        val callerContext = agent.runningExecutionContext

        // 2. Let calleeContext be a new ECMAScript code execution context.
        // 3. Set the Function of calleeContext to F.
        // 4. Let calleeRealm be F.[[Realm]].
        // 5. Set the Realm of calleeContext to calleeRealm.
        // 6. Set the ScriptOrModule of calleeContext to F.[[ScriptOrModule]].
        // 7. Let localEnv be NewFunctionEnvironment(F, newTarget).
        // 8. Set the LexicalEnvironment of calleeContext to localEnv.
        // 9. Set the VariableEnvironment of calleeContext to localEnv.
        // 10. Set the PrivateEnvironment of calleeContext to F.[[PrivateEnvironment]].
        val localEnv = newFunctionEnvironment(newTarget)

        val calleeContext = ExecutionContext(
            function = this,
            realm = realm,
            executable = callerContext.executable, // TODO: This seems wrong
            envRecord = localEnv,
        )

        // 11. If callerContext is not already suspended, suspend callerContext.

        // 12. Push calleeContext onto the execution context stack; calleeContext is now the running execution context.
        // 13. NOTE: Any exception objects produced after this point are associated with calleeRealm.
        agent.pushExecutionContext(calleeContext)

        // 14. Return calleeContext.
        return calleeContext
    }

    @ECMAImpl("10.2.1.2")
    protected fun ordinaryCallBindThis(calleeContext: ExecutionContext, thisArgument: JSValue) {
        // 1. Let thisMode be F.[[ThisMode]].
        // 2. If thisMode is lexical, return unused.
        if (thisMode == ThisMode.Lexical)
            return

        // 3. Let calleeRealm be F.[[Realm]].
        val calleeRealm = realm

        // 4. Let localEnv be the LexicalEnvironment of calleeContext.
        val localEnv = calleeContext.envRecord

        // 5. If thisMode is strict, let thisValue be thisArgument.
        val thisValue = if (thisMode == ThisMode.Strict) {
            thisArgument
        }
        // 6. Else,
        else {
            // a. If thisArgument is undefined or null, then
            if (thisArgument.isNullish) {
                // i. Let globalEnv be calleeRealm.[[GlobalEnv]].
                val globalEnv = calleeRealm.globalEnv

                // ii. Assert: globalEnv is a global Environment Record.
                // iii. Let thisValue be globalEnv.[[GlobalThisValue]].
                globalEnv.globalThisValue
            }
            // b. Else,
            else {
                // i. Let thisValue be ! ToObject(thisArgument).
                // ii. NOTE: ToObject produces wrapper objects using calleeRealm.
                thisArgument.toObject()
            }
        }

        // 7. Assert: localEnv is a function Environment Record.
        ecmaAssert(localEnv is FunctionEnvRecord)

        // 8. Assert: The next step never returns an abrupt completion because localEnv.[[ThisBindingStatus]] is not initialized.
        // 9. Perform ! localEnv.BindThisValue(thisValue).
        localEnv.bindThisValue(thisValue)

        // 10. Return unused.
    }

    @ECMAImpl("9.1.2.4")
    private fun newFunctionEnvironment(newTarget: JSValue): FunctionEnvRecord {
        // 1. Let env be a new function Environment Record containing no bindings.
        // 2. Set env.[[FunctionObject]] to F.
        // 3. If F.[[ThisMode]] is lexical, set env.[[ThisBindingStatus]] to lexical.
        // 4. Else, set env.[[ThisBindingStatus]] to uninitialized.
        // 5. Set env.[[NewTarget]] to newTarget.
        // 6. Set env.[[OuterEnv]] to F.[[Environment]].
        // 7. Return env.
        return FunctionEnvRecord(
            realm,
            environment,
            this,
            newTarget,
        )
    }
}

class NormalInterpretedFunction private constructor(
    realm: Realm,
    transformedSource: TransformedSource,
) : InterpretedFunction(realm, transformedSource) {
    override fun evaluate(arguments: JSArguments): JSValue {
        val args = listOf(arguments.thisValue, arguments.newTarget) + arguments
        val result = Interpreter(transformedSource, args).interpret()
        return result.valueOrElse { throw result.error() }
    }

    companion object {
        fun create(
            transformedSource: TransformedSource,
            realm: Realm = Agent.activeAgent.getActiveRealm(),
        ) = NormalInterpretedFunction(realm, transformedSource).initialize()
    }
}

class GeneratorInterpretedFunction private constructor(
    realm: Realm,
    transformedSource: TransformedSource,
) : InterpretedFunction(realm, transformedSource) {
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
                Agent.activeAgent.runningExecutionContext,
            )
        }

        return generatorObject
    }

    companion object {
        fun create(
            transformedSource: TransformedSource,
            realm: Realm = Agent.activeAgent.getActiveRealm(),
        ) = GeneratorInterpretedFunction(realm, transformedSource).initialize()
    }
}
