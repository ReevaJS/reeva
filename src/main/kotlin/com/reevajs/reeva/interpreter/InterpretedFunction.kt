package com.reevajs.reeva.interpreter

import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.core.environment.EnvRecord
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.functions.generators.JSGeneratorObject
import com.reevajs.reeva.transformer.TransformedSource

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
)

class NormalInterpretedFunction private constructor(
    realm: Realm,
    transformedSource: TransformedSource,
    outerEnvRecord: EnvRecord,
) : InterpretedFunction(realm, transformedSource, outerEnvRecord) {
    override fun evaluate(arguments: JSArguments): JSValue {
        val args = listOf(arguments.thisValue, arguments.newTarget) + arguments
        val result = Interpreter(realm, transformedSource, args, outerEnvRecord).interpret()
        return result.valueOrElse { throw result.error() }
    }

    companion object {
        fun create(realm: Realm, transformedSource: TransformedSource, outerEnvRecord: EnvRecord) =
            NormalInterpretedFunction(realm, transformedSource, outerEnvRecord).initialize()
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
                realm,
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
        fun create(realm: Realm, transformedSource: TransformedSource, outerEnvRecord: EnvRecord) =
            GeneratorInterpretedFunction(realm, transformedSource, outerEnvRecord).initialize()
    }
}
