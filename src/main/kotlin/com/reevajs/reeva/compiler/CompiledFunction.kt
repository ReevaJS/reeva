package com.reevajs.reeva.compiler

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.ExecutionContext
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSBuiltinFunction
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.functions.generators.JSGeneratorObject
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.PropertyKey
import com.reevajs.reeva.runtime.primitives.JSEmpty
import com.reevajs.reeva.runtime.primitives.JSNull
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.utils.*

abstract class CompiledFunction(
    realm: Realm,
    name: String,
    prototype: JSValue,
) : JSFunction(
    realm,
    name,
    ThisMode.Strict,
    true,
    prototype,
) {
    var environment = Agent.activeAgent.activeEnvRecord

    protected abstract fun evaluate(arguments: JSArguments): JSValue

    @ECMAImpl("10.2.1", "[[Call]]")
    override fun call(arguments: JSArguments): JSValue {
        val agent = Agent.activeAgent

        // 1.  Let callerContext be the running execution context.

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
        val thisArgument = ordinaryCallBindThis(arguments.thisValue)

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
            AOs.ordinaryCreateFromConstructor(
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
            thisArgument = ordinaryCallBindThis(thisArgument)

            // b.  Let initializeResult be InitializeInstanceElements(thisArgument, F).
            // c.  If initializeResult is an abrupt completion, then
            //     i.  Remove calleeContext from the execution context stack and restore callerContext as the running
            //         execution context.
            //     ii. Return Completion(initializeResult).
            // Note: Class fields are handled in the IR/bytecode, so no work is done here
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
            realm,
            this,
            environment,
            callerContext.executable,
            null
        )

        agent.pushExecutionContext(calleeContext)
        return calleeContext
    }

    @ECMAImpl("10.2.1.2")
    protected fun ordinaryCallBindThis(thisArgument: JSValue): JSValue {
        // NOTE: In the spec, this function binds the proper receiver value to the function environment.
        //       However, we don't use function environments, and instead pass the proper receiver
        //       and new.target values directly to the interpreter via its arguments. So this function
        //       returns the receiver value instead of binding.

        // 1. Let thisMode be F.[[ThisMode]].
        // 2. If thisMode is lexical, return unused.
        if (thisMode == ThisMode.Lexical)
            return thisArgument

        // 3. Let calleeRealm be F.[[Realm]].
        val calleeRealm = realm

        // 4. Let localEnv be the LexicalEnvironment of calleeContext.
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
        // 8. Assert: The next step never returns an abrupt completion because localEnv.[[ThisBindingStatus]] is not initialized.
        // 9. Perform ! localEnv.BindThisValue(thisValue).
        // 10. Return unused.
        return thisValue
    }

    protected fun commonInitialization(superClass: JSValue): JSValue {
        var protoParent: JSValue
        var constructorParent: JSValue
        
        when {
            superClass == JSEmpty ->  {
                protoParent = realm.objectProto
                constructorParent = realm.functionProto
            } 
            superClass == JSNull -> {
                protoParent = JSNull
                constructorParent = realm.functionProto
            }
            !AOs.isConstructor(superClass) -> Errors.NotACtor(superClass.toJSString().string).throwTypeError()
            else -> {
                protoParent = superClass.get("prototype")
                if (protoParent != JSNull && protoParent !is JSObject)
                    Errors.TODO("superClass.prototype invalid type").throwTypeError()
                constructorParent = superClass
            }
        }

        val proto = JSObject.create(proto = protoParent)

        AOs.makeClassConstructor(this)
        AOs.makeConstructor(this, false, getPrototype() as? JSObject)

        if (superClass != JSEmpty)
            constructorKind = ConstructorKind.Derived

        setPrototype(constructorParent)
        AOs.makeMethod(this, proto)
        AOs.createMethodProperty(proto, "constructor".key(), this)

        return proto
    }

    protected fun installMethod(name: PropertyKey, function: JSFunction, obj: JSObject) {
        AOs.makeMethod(function, obj)
        AOs.defineMethodProperty(name, obj, function, false)
    }
}
