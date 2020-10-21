package me.mattco.renva

import me.mattco.renva.runtime.Agent
import me.mattco.renva.runtime.Realm
import java.io.File

val outDirectory = File("./demo/out/")
val indexFile = File("./demo/index.js")

fun main(args: Array<String>) {
    val source = indexFile.readText()
    val realm = Realm()
    val scriptRecord = realm.parseScript(source)
    println(scriptRecord.scriptOrModule.dump(0))

    val agent = Agent("my agent")
    agent.execute(scriptRecord)
    println("poggers")
}