package com.reevajs.reeva.interpreter

import com.reevajs.reeva.core.environment.EnvRecord
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.functions.generators.JSGeneratorObject
import com.reevajs.reeva.runtime.primitives.JSEmpty
import com.reevajs.reeva.transformer.TransformedSource
import com.reevajs.reeva.utils.key

abstract class InterpretedFunction(
    val transformedSource: TransformedSource,
    val outerEnvRecord: EnvRecord,
    prototype: JSValue = transformedSource.realm.functionProto,
) : JSFunction(
    transformedSource.realm,
    transformedSource.functionInfo.name,
    transformedSource.functionInfo.isStrict,
    prototype,
)

class NormalInterpretedFunction private constructor(
    transformedSource: TransformedSource,
    outerEnvRecord: EnvRecord,
) : InterpretedFunction(transformedSource, outerEnvRecord) {
    override fun evaluate(arguments: JSArguments): JSValue {
        val args = listOf(arguments.thisValue, arguments.newTarget) + arguments
        val result = Interpreter(transformedSource, args, outerEnvRecord).interpret()
        return result.valueOrElse { throw result.error() }
    }

    companion object {
        fun create(transformedSource: TransformedSource, outerEnvRecord: EnvRecord) =
            NormalInterpretedFunction(transformedSource, outerEnvRecord).initialize()
    }
}

class GeneratorInterpretedFunction private constructor(
    transformedSource: TransformedSource,
    outerEnvRecord: EnvRecord,
) : InterpretedFunction(transformedSource, outerEnvRecord) {
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
        fun create(transformedSource: TransformedSource, outerEnvRecord: EnvRecord) =
            GeneratorInterpretedFunction(transformedSource, outerEnvRecord).initialize()
    }
}
