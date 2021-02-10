package me.mattco.reeva.runtime.functions

import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined

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
//    var fields: List<FieldRecord> = listOf()

    abstract fun evaluate(arguments: JSArguments): JSValue

    open fun call(arguments: JSArguments): JSValue {
        TODO()
//        if (isClassConstructor)
//            Errors.Class.CtorRequiresNew.throwTypeError()
//        val calleeContext = Operations.prepareForOrdinaryCall(this, JSUndefined)
//        ecmaAssert(Agent.runningContext == calleeContext)
//        Operations.ordinaryCallBindThis(this, calleeContext, thisValue)
//        return try {
//            evaluate(arguments)
//        } finally {
//            Agent.popContext()
//        }
    }

    open fun construct(arguments: JSArguments): JSValue {
        TODO()
//        ecmaAssert(newTarget is JSObject)
//
//        val thisArgument = if (constructorKind == ConstructorKind.Base) {
//            Operations.ordinaryCreateFromConstructor(newTarget, realm.objectProto)
//        } else null
//
//        val calleeContext = Operations.prepareForOrdinaryCall(this, newTarget)
//        ecmaAssert(Agent.runningContext == calleeContext)
//        if (constructorKind == ConstructorKind.Base) {
//            Operations.ordinaryCallBindThis(this, calleeContext, thisArgument!!)
//            try {
//                initializeInstanceFields(thisArgument, this)
//            } catch (e: ThrowException) {
//                Agent.popContext()
//                throw e
//            }
//        }
//        val constructorEnv = calleeContext.lexicalEnv
//        expect(constructorEnv is FunctionEnvRecord)
//
//        try {
//            val result = evaluate(arguments)
//            if (result is JSObject)
//                return result
//            if (constructorKind == ConstructorKind.Base)
//                return thisArgument!!
//            if (result != JSUndefined)
//                Errors.Class.ReturnObjectFromDerivedCtor.throwTypeError()
//        } finally {
//            Agent.popContext()
//        }
//
//        return constructorEnv.getThisBinding()
    }

//    data class FieldRecord(
//        val name: JSValue,
//        val initializer: JSValue,
//        val isAnonymousFunctionDefinition: Boolean,
//    )

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
//        @JvmStatic
//        fun initializeInstanceFields(obj: JSObject, ctor: JSFunction) {
//            ctor.fields.forEach {
//                defineField(obj, it)
//            }
//        }

//        private fun defineField(receiver: JSObject, fieldRecord: FieldRecord) {
//            val initValue = if (fieldRecord.initializer != JSEmpty) {
//                Operations.call(fieldRecord.initializer, receiver)
//            } else JSUndefined
//
//            if (fieldRecord.isAnonymousFunctionDefinition) {
//                if (!Operations.hasOwnProperty(initValue, "name".key()))
//                    Operations.setFunctionName(initValue as JSFunction, Operations.toPropertyKey(fieldRecord.name))
//            }
//
//            Operations.createDataPropertyOrThrow(receiver, fieldRecord.name, initValue)
//        }
    }
}
