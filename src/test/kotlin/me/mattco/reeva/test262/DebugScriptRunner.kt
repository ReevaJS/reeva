package me.mattco.reeva.test262

import me.mattco.reeva.Reeva
import me.mattco.reeva.runtime.Operations
import java.io.File

val outDirectory = File("./demo/out/")
val indexFile = File("./demo/index.js")
val test262HarnessFile = File("./demo/test262.js")

fun main() {
    Reeva.setup()

    val realm = Reeva.makeRealm()
    val test262Result = Reeva.evaluateScript(test262HarnessFile.readText(), realm)

    Reeva.with(realm) {
        if (test262Result.isError)
            println("\u001b[31m[test262] ${Operations.toString(test262Result.value).string}\u001B[0m")
    }

    val result = Reeva.evaluateModule(indexFile, realm)

    Reeva.with(realm) {
        if (result.isError) {
            println("\u001b[31m${Operations.toString(result.value).string}\u001B[0m")
        } else if (!result.value.isUndefined) {
            println(Operations.toPrintableString(result.value))
        }
    }

    Reeva.teardown()
}
