package me.mattco.reeva.runtime

import me.mattco.reeva.Reeva
import me.mattco.reeva.ast.statements.StatementList
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.interpreter.Interpreter
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSNumber
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.isRadixDigit
import me.mattco.reeva.utils.toValue

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
        defineNativeFunction("id", 1, ::id)
        defineNativeFunction("eval", 1, ::eval)
        defineNativeFunction("parseInt", 1, ::parseInt)

        defineNativeFunction("jvm", 1, ::jvm)

        // Debug method
        // TODO
//        defineNativeFunction("isStrict".key(), 0, 0) { Operations.isStrict().toValue() }
    }

    private fun id(arguments: JSArguments): JSValue {
        val o = arguments.argument(0)
        return "${o::class.java.simpleName}@${Integer.toHexString(o.hashCode())}".toValue()
    }

    private fun eval(arguments: JSArguments): JSValue {
        return performEval(arguments.argument(0), Reeva.activeAgent.activeRealm, strictCaller = false, direct = false)
    }

    private fun parseInt(arguments: JSArguments): JSValue {
        var inputString = Operations.trimString(Operations.toString(arguments.argument(0)), Operations.TrimType.Start)
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
        var radix = Operations.toInt32(arguments.argument(1)).asInt.let {
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

        val end = inputString.indexOfFirst { !it.isRadixDigit(radix) }.let { if (it == -1) inputString.length else it }
        val content = inputString.substring(0, end)
        if (content.isEmpty())
            return JSNumber.NaN
        val numericValue = content.toLongOrNull(radix) ?: return JSNumber.NaN
        return JSNumber(numericValue * sign)
    }

    private fun jvm(arguments: JSArguments): JSValue {
        // TODO
        return JSUndefined

//        if (arguments.isEmpty())
//            Errors.JVMCompat.JVMFuncNoArgs.throwTypeError()
//
//        if (arguments.any { it !is JSClassObject })
//            Errors.JVMCompat.JVMFuncBadArgType.throwTypeError()
//
//        val classObjects = arguments.map { (it as JSClassObject).clazz }
//        if (classObjects.count { it.isInterface } > 1)
//            Errors.JVMCompat.JVMFuncMultipleBaseClasses.throwTypeError()
//
//        classObjects.firstOrNull {
//            Modifier.isFinal(it.modifiers)
//        }?.let {
//            Errors.JVMCompat.JVMFuncFinalClass(it.name).throwTypeError()
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

    companion object {
        @ECMAImpl("18.2.1.1")
        fun performEval(argument: JSValue, callerRealm: Realm, strictCaller: Boolean, direct: Boolean): JSValue {
            // TODO
            Errors.Custom("eval is not yet implemented in Reeva").throwInternalError()

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
//                Error(parser.syntaxErrors.first().message).throwSyntaxError()
//
//            val body = scriptNode.statementList
//            if (!inFunction && body.contains("NewTargetExpressionNode"))
//                Errors.NewTargetOutsideFunc.throwSyntaxError()
//
//            if (!inMethod && body.contains("SuperPropertyExpressionNode"))
//                Errors.SuperOutsideMethod.throwSyntaxError()
//
//            if (!inDerivedConstructor && body.contains("SuperCallExpressionNode"))
//                Errors.SuperCallOutsideCtor.throwSyntaxError()
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
            interpreter: Interpreter,
        ) {
            // TODO
//            val varNames = body.varDeclaredNames()
//            val varDeclarations = body.varScopedDeclarations()
//            if (!strictEval) {
//                if (varEnv is GlobalEnvRecord) {
//                    varNames.forEach { name ->
//                        if (varEnv.hasLexicalDeclaration(name))
//                            Errors.TODO("evalDeclarationInstantiation 1").throwSyntaxError()
//                    }
//                }
//                var thisEnv = lexEnv
//                while (thisEnv != varEnv) {
//                    if (thisEnv !is ObjectEnvRecord) {
//                        varNames.forEach { name ->
//                            if (thisEnv.hasBinding(name))
//                                Errors.TODO("evalDeclarationInstantiation 2").throwSyntaxError()
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
//                            Errors.TODO("evalDeclarationInstantiation 3").throwTypeError()
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
//                                Errors.TODO("evalDeclarationInstantiation 4").throwTypeError()
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

        fun create(realm: Realm) = JSGlobalObject(realm).initialize()
    }
}
