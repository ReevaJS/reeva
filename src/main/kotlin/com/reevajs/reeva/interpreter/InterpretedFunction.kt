package com.reevajs.reeva.interpreter

import com.reevajs.reeva.compiler.Compiler
import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.ECMAScriptFunction
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.generators.JSGeneratorObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.transformer.TransformedSource

class NormalInterpretedFunction private constructor(
    realm: Realm,
    val transformedSource: TransformedSource,
) : ECMAScriptFunction(realm, transformedSource.functionInfo.name, transformedSource.functionInfo.isStrict) {
    override fun evaluate(arguments: JSArguments): JSValue {
        val compiled = Compiler(transformedSource).compile(realm)
        return if (arguments.newTarget != JSUndefined) {
            Operations.construct(compiled, arguments)
        } else Operations.call(compiled, arguments)

        // val args = listOf(arguments.thisValue, arguments.newTarget) + arguments
        // val result = Interpreter(transformedSource, args).interpret()
        // return result.valueOrElse { throw result.error() }
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
    val transformedSource: TransformedSource,
) : ECMAScriptFunction(realm, transformedSource.functionInfo.name, transformedSource.functionInfo.isStrict) {
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
