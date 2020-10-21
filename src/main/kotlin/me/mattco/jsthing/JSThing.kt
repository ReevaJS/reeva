package me.mattco.jsthing

import me.mattco.jsthing.compiler.Compiler
import me.mattco.jsthing.parser.Parser
import me.mattco.jsthing.runtime.Agent
import me.mattco.jsthing.runtime.Realm
import java.io.File

val outDirectory = File("./demo/out/")
val indexFile = File("./demo/index.js")

fun main(args: Array<String>) {
    val source = indexFile.readText()
    val realm = Realm()
    val scriptRecord = realm.parseScript(source)
    println(scriptRecord.scriptOrModule.dump(0))

    val agent = Agent("poggest agent")
    agent.execute(scriptRecord)
    println("poggers")
}
