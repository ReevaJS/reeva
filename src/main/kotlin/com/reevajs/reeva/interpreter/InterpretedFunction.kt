package com.reevajs.reeva.interpreter

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.ExecutionContext
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSBuiltinFunction
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.functions.JSUserFunction
import com.reevajs.reeva.runtime.functions.generators.JSGeneratorObject
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.toObject
import com.reevajs.reeva.transformer.FunctionInfo
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.ecmaAssert

abstract class InterpretedFunction(
    realm: Realm,
    val functionInfo: FunctionInfo,
    prototype: JSValue,
) : JSUserFunction(
    realm,
    functionInfo.name,
    when {
        functionInfo.isArrow -> ThisMode.Lexical
        functionInfo.isStrict -> ThisMode.Strict
        else -> ThisMode.Global
    },
    functionInfo.isStrict,
    prototype,
)

class NormalInterpretedFunction private constructor(
    realm: Realm,
    functionInfo: FunctionInfo,
) : InterpretedFunction(realm, functionInfo, realm.functionProto) {
    override fun evaluate(arguments: JSArguments): JSValue {
        val args = listOf(arguments.thisValue, arguments.newTarget) + arguments
        return Interpreter(functionInfo, args).interpret()
    }

    companion object {
        fun create(
            functionInfo: FunctionInfo,
            realm: Realm = Agent.activeAgent.getActiveRealm(),
        ) = NormalInterpretedFunction(realm, functionInfo).initialize()
    }
}

class GeneratorInterpretedFunction private constructor(
    realm: Realm,
    functionInfo: FunctionInfo,
) : InterpretedFunction(realm, functionInfo, realm.generatorFunctionProto) {
    private lateinit var generatorObject: JSGeneratorObject

    override fun init() {
        super.init()
        defineOwnProperty("prototype", realm.functionProto)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        if (!::generatorObject.isInitialized) {
            generatorObject = JSGeneratorObject.create(
                functionInfo,
                arguments.thisValue,
                arguments,
                Agent.activeAgent.runningExecutionContext,
            )
        }

        return generatorObject
    }

    companion object {
        fun create(
            functionInfo: FunctionInfo,
            realm: Realm = Agent.activeAgent.getActiveRealm(),
        ) = GeneratorInterpretedFunction(realm, functionInfo).initialize()
    }
}

class AsyncInterpretedFunction private constructor(
    realm: Realm,
    functionInfo: FunctionInfo,
) : InterpretedFunction(realm, functionInfo, realm.asyncFunctionProto) {
    override fun evaluate(arguments: JSArguments): JSValue {
        val promiseCapability = AOs.newPromiseCapability(realm.promiseCtor)

        val args = listOf(arguments.thisValue, arguments.newTarget) + arguments
        val interpreter = Interpreter(functionInfo, args, promiseCapability)

        val resultValue = interpreter.interpret()
        if (!interpreter.isAwaiting) {
            AOs.fulfillPromise(promiseCapability.promise as JSObject, resultValue)
        }

        return promiseCapability.promise
    }

    companion object {
        fun create(
            functionInfo: FunctionInfo,
            realm: Realm = Agent.activeAgent.getActiveRealm(),
        ) = AsyncInterpretedFunction(realm, functionInfo).initialize()
    }
}
