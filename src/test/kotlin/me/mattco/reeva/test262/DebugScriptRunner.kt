package me.mattco.reeva.test262

import me.mattco.reeva.Reeva
import me.mattco.reeva.core.Agent
import me.mattco.reeva.core.ExecutionContext
import me.mattco.reeva.core.Realm
import me.mattco.reeva.interpreter.Interpreter
import me.mattco.reeva.runtime.JSGlobalObject
import me.mattco.reeva.runtime.Operations
import java.io.File

val outDirectory = File("./demo/out/")
val indexFile = File("./demo/index.js")

fun main() {
    Reeva.setup()

    val source = indexFile.readText()
    val realm = Reeva.makeRealm()
    val result = Reeva.evaluate(source, realm)

    Reeva.with(realm) {
        if (result.isError) {
            println("\u001b[31m${Operations.toString(result.value).string}\u001B[0m")
        } else if (!result.value.isUndefined) {
            println(Operations.toString(result.value).string)
        }
    }


    Reeva.teardown()


//    val start = System.nanoTime()
//    val source = indexFile.readText()
//    val realm = Realm()
//    val scriptRecord = realm.parseScript(source)
////    println(scriptRecord.scriptOrModule.dump())
//    if (scriptRecord.errors.isNotEmpty()) {
//        for (error in scriptRecord.errors) {
//            println("SyntaxError (${error.lineNumber}, ${error.columnNumber}):\n    ${error.message}")
//        }
//    } else {
//        val agent = Agent()
//        val result = agent.interpretedEvaluation(scriptRecord)
//        if (result.error != null)
//            println("\u001b[31m${result.error}\u001B[0m")
//
//        println("\nTotal time: ${(System.nanoTime() - start) / 1_000_000}ms")
//    }
//
//    Reeva.execute("my script :)")
}

//fun interpretedEvaluation(scriptRecord: Realm.ScriptRecord, context: ExecutionContext? = null): Reeva.Result {
//    val realm = scriptRecord.realm
//    val newContext = context ?: ExecutionContext(this, realm, null)
//    runningContextStack.add(newContext)
//
//    if (!realm.isGloballyInitialized) {
//        realm.initObjects()
//        realm.setGlobalObject(JSGlobalObject.create(realm))
//    }
//
//    newContext.variableEnv = realm.globalEnv
//    newContext.lexicalEnv = realm.globalEnv
//
//    runningContextStack.add(newContext)
//
//    val start = System.nanoTime()
//    val interpreter = Interpreter(scriptRecord)
//    val interpretationResult = interpreter.interpret(newContext)
//    println("Interpretation time: ${(System.nanoTime() - start) / 1_000_000}ms")
//
//    return if (interpretationResult.isAbrupt) {
//        runningContext.error = null
//        EvaluationResult(realm.operations.toString(interpretationResult.value).string)
//    } else {
//        EvaluationResult(null)
//    }.also {
//        runningContextStack.remove(newContext)
//    }
//}
