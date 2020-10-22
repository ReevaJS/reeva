package me.mattco.reeva

import me.mattco.reeva.runtime.Agent
import me.mattco.reeva.runtime.Realm
import java.io.File

val outDirectory = File("./demo/out/")
val indexFile = File("./demo/index.js")

fun main(args: Array<String>) {
    val source = indexFile.readText()
    val realm = Realm()
    val scriptRecord = realm.parseScript(source)
    for (error in scriptRecord.errors) {
        println("SyntaxError (${error.lineNumber}, ${error.columnNumber}):\n    ${error.message}")
    }
    println(scriptRecord.scriptOrModule.dump(0))

    val agent = Agent("my agent")
    agent.execute(scriptRecord)
}
