package me.mattco.reeva.runtime.functions

import me.mattco.reeva.core.Agent
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ReturnException
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.core.environment.FunctionEnvRecord
import me.mattco.reeva.core.environment.ThisBindable
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSEmpty
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.*

abstract class JSFunction(
    realm: Realm,
    val thisMode: ThisMode,
    var envRecord: EnvRecord? = null,
    var homeObject: JSValue = JSUndefined,
    var isStrict: Boolean = false,
    prototype: JSValue = realm.functionProto,
) : JSObject(realm, prototype) {
    var constructorKind = ConstructorKind.Base

    var isCallable: Boolean = true
    var isConstructable: Boolean = false
    var isClassConstructor: Boolean = false
    var fields: List<FieldRecord> = listOf()

    abstract fun evaluate(arguments: JSArguments): JSValue

    open fun call(thisValue: JSValue, arguments: JSArguments): JSValue {
        if (isClassConstructor)
            Errors.Class.CtorRequiresNew.throwTypeError()
        val calleeContext = Operations.prepareForOrdinaryCall(this, JSUndefined)
        ecmaAssert(Agent.runningContext == calleeContext)
        Operations.ordinaryCallBindThis(this, calleeContext, thisValue)
        return try {
            evaluate(arguments)
        } catch (e: ReturnException) {
            // TODO: This is only necessary for the interpreter... can we move this
            // somewhere else?
            e.value
        } finally {
            Agent.popContext()
        }
    }

    open fun construct(arguments: JSArguments, newTarget: JSValue): JSValue {
        ecmaAssert(newTarget is JSObject)

        val thisArgument = if (constructorKind == ConstructorKind.Base) {
            Operations.ordinaryCreateFromConstructor(newTarget, realm.objectProto)
        } else null

        val calleeContext = Operations.prepareForOrdinaryCall(this, newTarget)
        ecmaAssert(Agent.runningContext == calleeContext)
        if (constructorKind == ConstructorKind.Base) {
            Operations.ordinaryCallBindThis(this, calleeContext, thisArgument!!)
            try {
                initializeInstanceFields(thisArgument, this)
            } catch (e: ThrowException) {
                Agent.popContext()
                throw e
            }
        }
        val constructorEnv = calleeContext.lexicalEnv
        expect(constructorEnv is FunctionEnvRecord)

        try {
            val result = evaluate(arguments)
            if (result is JSObject)
                return result
            if (constructorKind == ConstructorKind.Base)
                return thisArgument!!
            if (result != JSUndefined)
                Errors.Class.ReturnObjectFromDerivedCtor.throwTypeError()
        } catch (e: ReturnException) {
            // TODO: This is only necessary for the interpreter... can we move this
            // somewhere else?
            if (e.value is JSObject)
                return e.value
            if (constructorKind == ConstructorKind.Base)
                return thisArgument!!
            if (e.value != JSUndefined)
                Errors.Class.ReturnObjectFromDerivedCtor.throwTypeError()
        } finally {
            Agent.popContext()
        }

        return constructorEnv.getThisBinding()
    }

    protected val thisValue: JSValue
        get() {
            val envRec = Operations.getThisEnvironment()
            expect(envRec is ThisBindable)
            return envRec.getThisBinding()
        }

    protected val newTarget: JSValue
        get() {
            val envRec = Operations.getThisEnvironment()
            expect(envRec is FunctionEnvRecord)
            return envRec.newTarget
        }

    data class FieldRecord(
        val name: JSValue,
        val initializer: JSValue,
        val isAnonymousFunctionDefinition: Boolean,
    )

    enum class ThisMode {
        Lexical,
        NonLexical,
        Strict,
        Global
    }

    enum class ConstructorKind {
        Base,
        Derived,
    }

    companion object {
        @JvmStatic
        fun initializeInstanceFields(obj: JSObject, ctor: JSFunction) {
            ctor.fields.forEach {
                defineField(obj, it)
            }
        }

        private fun defineField(receiver: JSObject, fieldRecord: FieldRecord) {
            val initValue = if (fieldRecord.initializer != JSEmpty) {
                Operations.call(fieldRecord.initializer, receiver)
            } else JSUndefined

            if (fieldRecord.isAnonymousFunctionDefinition) {
                if (!Operations.hasOwnProperty(initValue, "name".key()))
                    Operations.setFunctionName(initValue as JSFunction, Operations.toPropertyKey(fieldRecord.name))
            }

            Operations.createDataPropertyOrThrow(receiver, fieldRecord.name, initValue)
        }
    }
}
