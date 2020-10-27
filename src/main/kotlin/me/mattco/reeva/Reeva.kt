package me.mattco.reeva

import me.mattco.reeva.runtime.Agent
import me.mattco.reeva.runtime.Realm
import java.io.File

val outDirectory = File("./demo/out/")
val indexFile = File("./demo/index.js")

fun main(args: Array<String>) {
    val start = System.nanoTime()
    val source = indexFile.readText()
    val realm = Realm()
    val scriptRecord = realm.parseScript(source)
//    println(scriptRecord.scriptOrModule.dump())
    if (scriptRecord.errors.isNotEmpty()) {
        for (error in scriptRecord.errors) {
            println("SyntaxError (${error.lineNumber}, ${error.columnNumber}):\n    ${error.message}")
        }
    } else {
        val agent = Agent("my agent")
        val result = agent.interpretedEvaluation(scriptRecord)
        if (result.error != null)
            println("\u001b[31m${result.error}\u001B[0m")

        println("\nTotal time: ${(System.nanoTime() - start) / 1_000_000}ms")
    }
}
