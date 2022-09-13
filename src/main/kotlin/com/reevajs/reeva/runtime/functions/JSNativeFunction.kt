package com.reevajs.reeva.runtime.functions

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.ExecutionContext
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.primitives.JSEmpty
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.NativeFunctionSignature
import com.reevajs.reeva.utils.toValue
import java.lang.reflect.InvocationTargetException
import java.util.function.Function

/**
 * This implements what the spec calls a builtin function. This is called NativeFunction
 * because BuiltinFunction is reserved for those functions which use a MethodHandle to
 * a static method for their implementation.
 */
abstract class JSNativeFunction protected constructor(
    realm: Realm,
    protected val name: String,
    protected val length: Int,
    prototype: JSValue = realm.functionProto,
    debugName: String = name,
) : JSFunction(realm, debugName, thisMode = ThisMode.Lexical, prototype = prototype) {
    override fun init() {
        super.init()

        defineOwnProperty("length", length.toValue(), Descriptor.CONFIGURABLE)
        defineOwnProperty("name", name.toValue(), Descriptor.CONFIGURABLE)
    }

    protected abstract fun evaluate(arguments: JSArguments): JSValue

    @ECMAImpl("10.3.1", "[[Call]]")
    override fun call(arguments: JSArguments): JSValue {
        return callConstructImpl(arguments, isConstruct = false)
    }

    @ECMAImpl("10.3.2", "[[Construct]]")
    override fun construct(arguments: JSArguments): JSValue {
        return callConstructImpl(arguments, isConstruct = true)
    }

    private fun callConstructImpl(arguments: JSArguments, isConstruct: Boolean): JSValue {
        val agent = Agent.activeAgent

        // 1.  Let callerContext be the running execution context.
        val callerContext = agent.runningExecutionContext

        // 2.  If callerContext is not already suspended, suspend callerContext.

        // 3.  Let calleeContext be a new execution context.
        // 4.  Set the Function of calleeContext to F.
        // 5.  Let calleeRealm be F.[[Realm]]
        // 6.  Set the Realm of calleeContext to calleeRealm.
        // 7.  Set the ScriptOrModule of calleeContext to null.
        // 8.  Perform any necessary implementation-defined initialization of calleeContext.
        val calleeContext = ExecutionContext(
            this,
            realm,
            null,
            null,
            null,
        )

        // 9.  Push calleeContext onto the execution context stack; calleeContext is now the running execution context.
        agent.pushExecutionContext(calleeContext)

        return try {
            val actualArguments = if (isConstruct) {
                // 10.  Let result be the CompletionRecord that is the result of evaluating F in a manner that conforms
                //      to the specification of F. The this value is uninitialized, argumentsList providers the named
                //      parameters, and newTarget provides the NewTarget value.
                arguments.withThisValue(JSEmpty)
            } else {
                // 10. Let result be the Completion Record that is the result of evaluating F in a manner that conforms
                //     to the specification of F. thisArgument is the this value, argumentsList provides the named
                //     parameters, and thew NewTarget value is undefined.
                arguments.withNewTarget(JSUndefined)

            }

            // 12. Return result.
            evaluate(actualArguments)
        } finally {
            // 11. Remove calleeContext from the execution context stack and restore callerContext as the running
            //     execution context.
            agent.popExecutionContext()
        }
    }
}
