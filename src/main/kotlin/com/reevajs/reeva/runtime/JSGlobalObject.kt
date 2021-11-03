package com.reevajs.reeva.runtime

import com.reevajs.reeva.ast.statements.StatementList
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.core.environment.EnvRecord
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSNumber
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.isRadixDigit
import com.reevajs.reeva.utils.toValue

open class JSGlobalObject protected constructor(
    realm: Realm,
    proto: JSObject = realm.objectProto
) : JSObject(realm, proto) {
    override fun init() {
        super.init()

        val attrs = Descriptor.CONFIGURABLE or Descriptor.WRITABLE
        defineOwnProperty("Array", realm.arrayCtor, attrs)
        defineOwnProperty("ArrayBuffer", realm.arrayBufferCtor, attrs)
        defineOwnProperty("BigInt", realm.bigIntCtor, attrs)
        defineOwnProperty("Boolean", realm.booleanCtor, attrs)
        defineOwnProperty("DataView", realm.dataViewCtor, attrs)
        defineOwnProperty("Date", realm.dateCtor, attrs)
        defineOwnProperty("Error", realm.errorCtor, attrs)
        defineOwnProperty("EvalError", realm.evalErrorCtor, attrs)
        defineOwnProperty("Function", realm.functionCtor, attrs)
        defineOwnProperty("InternalError", realm.internalErrorCtor, attrs)
        defineOwnProperty("Map", realm.mapCtor, attrs)
        defineOwnProperty("Number", realm.numberCtor, attrs)
        defineOwnProperty("Object", realm.objectCtor, attrs)
        defineOwnProperty("Packages", realm.packageObj, attrs)
        defineOwnProperty("Promise", realm.promiseCtor, attrs)
        defineOwnProperty("Proxy", realm.proxyCtor, attrs)
        defineOwnProperty("RangeError", realm.rangeErrorCtor, attrs)
        defineOwnProperty("ReferenceError", realm.referenceErrorCtor, attrs)
        defineOwnProperty("Reflect", realm.reflectObj, attrs)
        defineOwnProperty("RegExp", realm.regExpCtor, attrs)
        defineOwnProperty("Set", realm.setCtor, attrs)
        defineOwnProperty("String", realm.stringCtor, attrs)
        defineOwnProperty("Symbol", realm.symbolCtor, attrs)
        defineOwnProperty("SyntaxError", realm.syntaxErrorCtor, attrs)
        defineOwnProperty("TypeError", realm.typeErrorCtor, attrs)
        defineOwnProperty("URIError", realm.uriErrorCtor, attrs)

        defineOwnProperty("Math", realm.mathObj, attrs)
        defineOwnProperty("JSON", realm.jsonObj, attrs)
        defineOwnProperty("console", realm.consoleObj, attrs)

        defineOwnProperty("Int8Array", realm.int8ArrayCtor, attrs)
        defineOwnProperty("Uint8Array", realm.uint8ArrayCtor, attrs)
        defineOwnProperty("Uint8ClampedArray", realm.uint8CArrayCtor, attrs)
        defineOwnProperty("Int16Array", realm.int16ArrayCtor, attrs)
        defineOwnProperty("Uint16Array", realm.uint16ArrayCtor, attrs)
        defineOwnProperty("Int32Array", realm.int32ArrayCtor, attrs)
        defineOwnProperty("Uint32Array", realm.uint32ArrayCtor, attrs)
        defineOwnProperty("Float32Array", realm.float32ArrayCtor, attrs)
        defineOwnProperty("Float64Array", realm.float64ArrayCtor, attrs)
        defineOwnProperty("BigInt64Array", realm.bigInt64ArrayCtor, attrs)
        defineOwnProperty("BigUint64Array", realm.bigUint64ArrayCtor, attrs)

        defineOwnProperty("Infinity", JSNumber.POSITIVE_INFINITY, 0)
        defineOwnProperty("NaN", JSNumber.NaN, 0)
        defineOwnProperty("globalThis", this, Descriptor.WRITABLE or Descriptor.CONFIGURABLE)
        defineOwnProperty("undefined", JSUndefined, 0)

        defineBuiltin("isNaN", 1, ReevaBuiltin.GlobalIsNaN)
        defineBuiltin("id", 1, ReevaBuiltin.GlobalId)
        defineBuiltin("eval", 1, ReevaBuiltin.GlobalEval)
        defineBuiltin("parseInt", 1, ReevaBuiltin.GlobalParseInt)
        defineBuiltin("jvm", 1, ReevaBuiltin.GlobalJvm)
        defineBuiltin("inspect", 1, ReevaBuiltin.GlobalInspect)

        // Debug method
        // TODO
        // defineNativeFunction("isStrict".key(), 0, 0) { Operations.isStrict().toValue() }
    }

    companion object {
        fun create(realm: Realm) = JSGlobalObject(realm).initialize()

        @JvmStatic
        @ECMAImpl("19.2.3")
        fun isNaN(realm: Realm, arguments: JSArguments): JSValue {
            val num = arguments.argument(0).toNumber(realm)
            return (num == JSNumber.NaN).toValue()
        }

        @ECMAImpl("18.2.1.1")
        fun performEval(
            realm: Realm,
            argument: JSValue,
            callerRealm: Realm,
            strictCaller: Boolean,
            direct: Boolean,
        ): JSValue {
            // TODO
            Errors.Custom("eval is not yet implemented in Reeva").throwInternalError(realm)

//            if (!direct)
//                ecmaAssert(!strictCaller)
//
//            if (argument !is JSString)
//                return argument
//
//            val evalRealm = Agent.runningContext.realm
//            var inFunction = false
//            var inMethod = false
//            var inDerivedConstructor = false
//
//            if (direct) {
//                val thisEnv = Operations.getThisEnvironment()
//                if (thisEnv is FunctionEnvRecord) {
//                    val function = thisEnv.function
//                    inFunction = true
//                    inMethod = thisEnv.hasSuperBinding()
//                    if (function.constructorKind == JSFunction.ConstructorKind.Derived)
//                        inDerivedConstructor = true
//                }
//            }
//
//            val parser = Parser(argument.string)
//            val scriptNode = parser.parseScript()
//
//            if (Reeva.PRINT_PARSE_NODES) {
//                println("==== eval script ====")
//                println(scriptNode.dump(1))
//            }
//
//            if (parser.syntaxErrors.isNotEmpty())
//                Error(parser.syntaxErrors.first().message).throwSyntaxError(realm)
//
//            val body = scriptNode.statementList
//            if (!inFunction && body.contains("NewTargetExpressionNode"))
//                Errors.NewTargetOutsideFunc.throwSyntaxError(realm)
//
//            if (!inMethod && body.contains("SuperPropertyExpressionNode"))
//                Errors.SuperOutsideMethod.throwSyntaxError(realm)
//
//            if (!inDerivedConstructor && body.contains("SuperCallExpressionNode"))
//                Errors.SuperCallOutsideCtor.throwSyntaxError(realm)
//
//            val strictEval = strictCaller || scriptNode.statementList.hasUseStrictDirective()
//            val context = Agent.runningContext
//
//            var varEnv: EnvRecord
//            val lexEnv: EnvRecord
//
//            if (direct) {
//                varEnv = context.variableEnv!!
//                lexEnv = DeclarativeEnvRecord.create(context.lexicalEnv)
//            } else {
//                varEnv = evalRealm.globalEnv
//                lexEnv = DeclarativeEnvRecord.create(evalRealm.globalEnv)
//            }
//
//            if (strictEval)
//                varEnv = lexEnv
//
//            val evalContext = ExecutionContext(evalRealm, null)
//            evalContext.variableEnv = varEnv
//            evalContext.lexicalEnv = lexEnv
//            Agent.pushContext(evalContext)
//
//            val interpreter = Interpreter(callerRealm)
//
//            try {
//                evalDeclarationInstantiation(scriptNode.statementList, varEnv, lexEnv, strictEval, interpreter)
//                return interpreter.interpretScript(scriptNode).let { if (it == JSEmpty) JSUndefined else it }
//            } finally {
//                Agent.popContext()
//            }
        }

        private fun evalDeclarationInstantiation(
            body: StatementList,
            varEnv: EnvRecord,
            lexEnv: EnvRecord,
            strictEval: Boolean,
        ) {
            // TODO
//            val varNames = body.varDeclaredNames()
//            val varDeclarations = body.varScopedDeclarations()
//            if (!strictEval) {
//                if (varEnv is GlobalEnvRecord) {
//                    varNames.forEach { name ->
//                        if (varEnv.hasLexicalDeclaration(name))
//                            Errors.TODO("evalDeclarationInstantiation 1").throwSyntaxError(realm)
//                    }
//                }
//                var thisEnv = lexEnv
//                while (thisEnv != varEnv) {
//                    if (thisEnv !is ObjectEnvRecord) {
//                        varNames.forEach { name ->
//                            if (thisEnv.hasBinding(name))
//                                Errors.TODO("evalDeclarationInstantiation 2").throwSyntaxError(realm)
//                        }
//                    }
//                    thisEnv = thisEnv.outerEnv!!
//                }
//            }
//            val functionsToInitialize = mutableListOf<FunctionDeclarationNode>()
//            val declaredFunctionNames = mutableListOf<String>()
//            varDeclarations.asReversed().forEach { decl ->
//                if (decl is VariableDeclarationNode || decl is ForBindingNode || decl is BindingIdentifierNode)
//                    return@forEach
//                val functionName = decl.boundNames()[0]
//                if (functionName !in declaredFunctionNames) {
//                    if (varEnv is GlobalEnvRecord) {
//                        if (!varEnv.canDeclareGlobalFunction(functionName))
//                            Errors.TODO("evalDeclarationInstantiation 3").throwTypeError(realm)
//                        declaredFunctionNames.add(functionName)
//                        functionsToInitialize.add(0, decl as FunctionDeclarationNode)
//                    }
//                }
//            }
//
//            val declaredVarNames = mutableListOf<String>()
//            varDeclarations.forEach { decl ->
//                if (decl !is VariableDeclarationNode && decl !is ForBindingNode && decl !is BindingIdentifierNode)
//                    return@forEach
//                decl.boundNames().forEach { name ->
//                    if (name !in declaredFunctionNames) {
//                        if (varEnv is GlobalEnvRecord) {
//                            if (!varEnv.canDeclareGlobalVar(name))
//                                Errors.TODO("evalDeclarationInstantiation 4").throwTypeError(realm)
//                        }
//                        if (name !in declaredVarNames)
//                            declaredVarNames.add(name)
//                    }
//                }
//            }
//            body.lexicallyScopedDeclarations().forEach { decl ->
//                decl.boundNames().forEach { name ->
//                    if (decl.isConstantDeclaration()) {
//                        lexEnv.createImmutableBinding(name, true)
//                    } else {
//                        lexEnv.createMutableBinding(name, false)
//                    }
//                }
//            }
//            functionsToInitialize.forEach { func ->
//                val functionName = func.boundNames()[0]
//                val function = interpreter.instantiateFunctionObject(func, lexEnv)
//                if (varEnv is GlobalEnvRecord) {
//                    varEnv.createGlobalFunctionBinding(functionName, function, true)
//                } else {
//                    if (!varEnv.hasBinding(functionName)) {
//                        varEnv.createMutableBinding(functionName, true)
//                        // TODO: Validate above step
//                        varEnv.initializeBinding(functionName, function)
//                    } else {
//                        varEnv.setMutableBinding(functionName, function, false)
//                    }
//                }
//            }
//            declaredVarNames.forEach { name ->
//                if (varEnv is GlobalEnvRecord) {
//                    varEnv.createGlobalVarBinding(name, true)
//                } else if (!varEnv.hasBinding(name)) {
//                    varEnv.createMutableBinding(name, true)
//                    // TODO: Validate above step
//                    varEnv.initializeBinding(name, JSUndefined)
//                }
//            }
        }

        @JvmStatic
        fun id(realm: Realm, arguments: JSArguments): JSValue {
            val o = arguments.argument(0)
            return "${o::class.java.simpleName}@${Integer.toHexString(o.hashCode())}".toValue()
        }

        @JvmStatic
        fun eval(realm: Realm, arguments: JSArguments): JSValue {
            return performEval(realm, arguments.argument(0), realm, strictCaller = false, direct = false)
        }

        @JvmStatic
        fun parseInt(realm: Realm, arguments: JSArguments): JSValue {
            var inputString = Operations.trimString(
                realm,
                Operations.toString(realm, arguments.argument(0)),
                Operations.TrimType.Start,
            )
            val sign = when {
                inputString.startsWith("-") -> {
                    inputString = inputString.substring(1)
                    -1
                }
                inputString.startsWith("+") -> {
                    inputString = inputString.substring(1)
                    1
                }
                else -> 1
            }

            var stripPrefix = true
            var radix = Operations.toInt32(realm, arguments.argument(1)).asInt.let {
                if (it != 0) {
                    if (it !in 2..36)
                        return JSNumber.NaN
                    if (it != 16)
                        stripPrefix = false
                    it
                } else 10
            }

            if (stripPrefix && inputString.lowercase().startsWith("0x")) {
                inputString = inputString.substring(2)
                radix = 16
            }

            val end = inputString.indexOfFirst { !it.isRadixDigit(radix) }
                .let { if (it == -1) inputString.length else it }
            val content = inputString.substring(0, end)
            if (content.isEmpty())
                return JSNumber.NaN
            val numericValue = content.toLongOrNull(radix) ?: return JSNumber.NaN
            return JSNumber(numericValue * sign)
        }

        @JvmStatic
        fun jvm(realm: Realm, arguments: JSArguments): JSValue {
            // TODO
            return JSUndefined

//        if (arguments.isEmpty())
//            Errors.JVMCompat.JVMFuncNoArgs.throwTypeError(realm)
//
//        if (arguments.any { it !is JSClassObject })
//            Errors.JVMCompat.JVMFuncBadArgType.throwTypeError(realm)
//
//        val classObjects = arguments.map { (it as JSClassObject).clazz }
//        if (classObjects.count { it.isInterface } > 1)
//            Errors.JVMCompat.JVMFuncMultipleBaseClasses.throwTypeError(realm)
//
//        classObjects.firstOrNull {
//            Modifier.isFinal(it.modifiers)
//        }?.let {
//            Errors.JVMCompat.JVMFuncFinalClass(it.name).throwTypeError(realm)
//        }
//
//        val baseClass = classObjects.firstOrNull { !it.isInterface }
//        val interfaces = classObjects.filter { it.isInterface }
//
//        return JSClassObject.create(
//            realm,
//            ProxyClassCompiler().makeProxyClass(baseClass, interfaces)
//        )
        }

        @JvmStatic
        fun inspect(realm: Realm, arguments: JSArguments): JSValue {
            val value = arguments.argument(0)
            println(inspect(value, simple = false).stringify())
            return JSUndefined
        }
    }
}
