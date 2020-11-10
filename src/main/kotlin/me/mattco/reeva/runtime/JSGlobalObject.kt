package me.mattco.reeva.runtime

import me.mattco.reeva.Reeva
import me.mattco.reeva.ast.BindingIdentifierNode
import me.mattco.reeva.ast.FunctionDeclarationNode
import me.mattco.reeva.ast.ScriptOrModule
import me.mattco.reeva.ast.statements.ForBindingNode
import me.mattco.reeva.ast.statements.StatementListNode
import me.mattco.reeva.ast.statements.VariableDeclarationNode
import me.mattco.reeva.core.Agent
import me.mattco.reeva.core.Realm
import me.mattco.reeva.interpreter.Completion
import me.mattco.reeva.interpreter.Interpreter
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.core.ExecutionContext
import me.mattco.reeva.core.environment.*
import me.mattco.reeva.parser.Parser
import me.mattco.reeva.runtime.errors.JSSyntaxErrorObject
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSEmpty
import me.mattco.reeva.runtime.primitives.JSNumber
import me.mattco.reeva.runtime.primitives.JSString
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.*

open class JSGlobalObject protected constructor(
    realm: Realm,
    proto: JSObject = realm.objectProto
) : JSObject(realm, proto) {
    override fun init() {
        super.init()

        val attrs = Descriptor.CONFIGURABLE or Descriptor.WRITABLE
        defineOwnProperty("Array", realm.arrayCtor, attrs)
        defineOwnProperty("Boolean", realm.booleanCtor, attrs)
        defineOwnProperty("Date", realm.dateCtor, attrs)
        defineOwnProperty("Error", realm.errorCtor, attrs)
        defineOwnProperty("EvalError", realm.evalErrorCtor, attrs)
        defineOwnProperty("Function", realm.functionCtor, attrs)
        defineOwnProperty("Map", realm.mapCtor, attrs)
        defineOwnProperty("Number", realm.numberCtor, attrs)
        defineOwnProperty("Object", realm.objectCtor, attrs)
        defineOwnProperty("Promise", realm.promiseCtor, attrs)
        defineOwnProperty("Proxy", realm.proxyCtor, attrs)
        defineOwnProperty("RangeError", realm.rangeErrorCtor, attrs)
        defineOwnProperty("ReferenceError", realm.referenceErrorCtor, attrs)
        defineOwnProperty("Reflect", realm.reflectObj, attrs)
        defineOwnProperty("Set", realm.setCtor, attrs)
        defineOwnProperty("String", realm.stringCtor, attrs)
        defineOwnProperty("Symbol", realm.symbolCtor, attrs)
        defineOwnProperty("SyntaxError", realm.syntaxErrorCtor, attrs)
        defineOwnProperty("TypeError", realm.typeErrorCtor, attrs)
        defineOwnProperty("URIError", realm.uriErrorCtor, attrs)

        defineOwnProperty("Math", realm.mathObj, attrs)
        defineOwnProperty("JSON", realm.jsonObj, attrs)
        defineOwnProperty("console", realm.consoleObj, attrs)

        defineOwnProperty("Infinity", JSNumber.POSITIVE_INFINITY, 0)
        defineOwnProperty("NaN", JSNumber.NaN, 0)
        defineOwnProperty("globalThis", this, Descriptor.WRITABLE or Descriptor.CONFIGURABLE)
        defineOwnProperty("undefined", JSUndefined, 0)
        defineNativeFunction("id".key(), 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE, ::id)
        defineNativeFunction("eval".key(), 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE, ::eval)

        // Debug method
        defineNativeFunction("isStrict".key(), 0, 0) { _, _ -> Operations.isStrict().toValue() }
    }

    fun id(thisValue: JSValue, arguments: JSArguments): JSValue {
        val o = arguments.argument(0)
        return "${o::class.java.simpleName}@${Integer.toHexString(o.hashCode())}".toValue()
    }

    fun eval(thisValue: JSValue, arguments: JSArguments): JSValue {
        return performEval(arguments.argument(0), Agent.runningContext.realm, strictCaller = false, direct = false)
    }

    companion object {
        @JSThrows @ECMAImpl("18.2.1.1")
        fun performEval(argument: JSValue, callerRealm: Realm, strictCaller: Boolean, direct: Boolean): JSValue {
            if (!direct)
                ecmaAssert(!strictCaller)

            if (argument !is JSString)
                return argument

            val evalRealm = Agent.runningContext.realm
            var inFunction = false
            var inMethod = false
            var inDerivedConstructor = false

            if (direct) {
                val thisEnv = Operations.getThisEnvironment()
                if (thisEnv is FunctionEnvRecord) {
                    val function = thisEnv.function
                    inFunction = true
                    inMethod = thisEnv.hasSuperBinding()
                    if (function.constructorKind == JSFunction.ConstructorKind.Derived)
                        inDerivedConstructor = true
                }
            }

            val parser = Parser(argument.string, evalRealm)
            val scriptNode = parser.parseScript()

            if (Reeva.PRINT_PARSE_NODES) {
                println("==== eval script ====")
                println(scriptNode.dump(1))
            }

            if (parser.syntaxErrors.isNotEmpty())
                Error(parser.syntaxErrors.first().message).throwSyntaxError()

            val body = scriptNode.statementList
            if (!inFunction && body.contains("NewTargetNode"))
                Errors.NewTargetOutsideFunc.throwSyntaxError()

            if (!inMethod && body.contains("SuperPropertyNode"))
                Errors.SuperOutsideMethod.throwSyntaxError()

            if (!inDerivedConstructor && body.contains("SuperCallNode"))
                Errors.SuperCallOutsideCtor.throwSyntaxError()

            val strictEval = strictCaller || scriptNode.statementList.hasUseStrictDirective()
            val context = Agent.runningContext

            var varEnv: EnvRecord
            val lexEnv: EnvRecord

            if (direct) {
                varEnv = context.variableEnv!!
                lexEnv = DeclarativeEnvRecord.create(context.lexicalEnv)
            } else {
                varEnv = evalRealm.globalEnv
                lexEnv = DeclarativeEnvRecord.create(evalRealm.globalEnv)
            }

            if (strictEval)
                varEnv = lexEnv

            val evalContext = ExecutionContext(evalRealm, null)
            evalContext.variableEnv = varEnv
            evalContext.lexicalEnv = lexEnv
            Agent.pushContext(evalContext)

            val interpreter = Interpreter(callerRealm, ScriptOrModule(scriptNode))

            try {
                evalDeclarationInstantiation(scriptNode.statementList, varEnv, lexEnv, strictEval, interpreter)
                return interpreter.interpretScript().let { if (it == JSEmpty) JSUndefined else it }
            } finally {
                Agent.popContext()
            }
        }

        private fun evalDeclarationInstantiation(
            body: StatementListNode,
            varEnv: EnvRecord,
            lexEnv: EnvRecord,
            strictEval: Boolean,
            interpreter: Interpreter,
        ) {
            val varNames = body.varDeclaredNames()
            val varDeclarations = body.varScopedDeclarations()
            if (!strictEval) {
                if (varEnv is GlobalEnvRecord) {
                    varNames.forEach { name ->
                        if (varEnv.hasLexicalDeclaration(name))
                            Errors.TODO("evalDeclarationInstantiation 1").throwSyntaxError()
                    }
                }
                var thisEnv = lexEnv
                while (thisEnv != varEnv) {
                    if (thisEnv !is ObjectEnvRecord) {
                        varNames.forEach { name ->
                            if (thisEnv.hasBinding(name))
                                Errors.TODO("evalDeclarationInstantiation 2").throwSyntaxError()
                        }
                    }
                    thisEnv = thisEnv.outerEnv!!
                }
            }
            val functionsToInitialize = mutableListOf<FunctionDeclarationNode>()
            val declaredFunctionNames = mutableListOf<String>()
            varDeclarations.asReversed().forEach { decl ->
                if (decl is VariableDeclarationNode || decl is ForBindingNode || decl is BindingIdentifierNode)
                    return@forEach
                val functionName = decl.boundNames()[0]
                if (functionName !in declaredFunctionNames) {
                    if (varEnv is GlobalEnvRecord) {
                        if (!varEnv.canDeclareGlobalFunction(functionName))
                            Errors.TODO("evalDeclarationInstantiation 3").throwTypeError()
                        declaredFunctionNames.add(functionName)
                        functionsToInitialize.add(0, decl as FunctionDeclarationNode)
                    }
                }
            }

            val declaredVarNames = mutableListOf<String>()
            varDeclarations.forEach { decl ->
                if (decl !is VariableDeclarationNode && decl !is ForBindingNode && decl !is BindingIdentifierNode)
                    return@forEach
                decl.boundNames().forEach { name ->
                    if (name !in declaredFunctionNames) {
                        if (varEnv is GlobalEnvRecord) {
                            if (!varEnv.canDeclareGlobalVar(name))
                                Errors.TODO("evalDeclarationInstantiation 4").throwTypeError()
                        }
                        if (name !in declaredVarNames)
                            declaredVarNames.add(name)
                    }
                }
            }
            body.lexicallyScopedDeclarations().forEach { decl ->
                decl.boundNames().forEach { name ->
                    if (decl.isConstantDeclaration()) {
                        lexEnv.createImmutableBinding(name, true)
                    } else {
                        lexEnv.createMutableBinding(name, false)
                    }
                }
            }
            functionsToInitialize.forEach { func ->
                val functionName = func.boundNames()[0]
                val function = interpreter.instantiateFunctionObject(func, lexEnv)
                if (varEnv is GlobalEnvRecord) {
                    varEnv.createGlobalFunctionBinding(functionName, function, true)
                } else {
                    if (!varEnv.hasBinding(functionName)) {
                        varEnv.createMutableBinding(functionName, true)
                        // TODO: Validate above step
                        varEnv.initializeBinding(functionName, function)
                    } else {
                        varEnv.setMutableBinding(functionName, function, false)
                    }
                }
            }
            declaredVarNames.forEach { name ->
                if (varEnv is GlobalEnvRecord) {
                    varEnv.createGlobalVarBinding(name, true)
                } else if (!varEnv.hasBinding(name)) {
                    varEnv.createMutableBinding(name, true)
                    // TODO: Validate above step
                    varEnv.initializeBinding(name, JSUndefined)
                }
            }
        }
        fun create(realm: Realm) = JSGlobalObject(realm).also { it.init() }
    }
}
