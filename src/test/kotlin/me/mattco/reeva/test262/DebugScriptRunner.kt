package me.mattco.reeva.test262

import me.mattco.reeva.Reeva
import me.mattco.reeva.core.Agent
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.*
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.objects.JSObject.Companion.initialize
import me.mattco.reeva.runtime.primitives.JSUndefined
import java.io.File

val outDirectory = File("./demo/out/")
val indexFile = File("./demo/index.js")
val test262HarnessFile = File("./demo/test262.js")

interface JVMToJSConverter {
    fun isConvertible(value: Any): Boolean = true

    fun convert(value: Any): JSValue
}

interface JSToJVMConverter {
    fun isConvertible(value: JSValue): Boolean = true

    fun convert(value: JSValue): Any
}

fun main() {
    // CT example

    val script = "..."

    Reeva.setup()

    val agent = Agent().apply {
//        allowWithStatement = false
//        allowEvalFunction = false
//        allowNewFunction = true
//        errorReporter = SomeErrorReporterClass(Console.out)
//
//        debugOutput.emitAST = true
//        debugOutput.emitIR = true
//        debugOutput.emitClassFiles = true
//        debugOutput.path = File("./dump")
    }

    class CTGlobalObject(realm: Realm) : JSGlobalObject(realm) {
        private val triggers = mutableMapOf<String, MutableList<JSFunction>>()

        override fun init() {
            super.init()

            defineNativeFunction("register", 2, 0, ::register)
        }

        private fun register(arguments: JSArguments): JSValue {
            val triggerType = arguments.argument(0).toJSString().string
            val function = arguments.argument(1)
            if (!function.isCallable)
                TODO()

            if (triggerType !in triggers)
                triggers[triggerType] = mutableListOf()

            triggers[triggerType]!!.add(function as JSFunction)
            return JSUndefined
        }

        fun trigger(type: String) {
            triggers[type]?.forEach {
                it.call(JSArguments(listOf(), this))
            }
        }
    }

    Reeva.setAgent(agent)
    val realm = Reeva.makeRealm {
        JSGlobalObject.create(it).initialize()
    }

    agent.run(script, realm)

    Reeva.teardown()


//    val realm = Reeva.makeRealm()
//    val test262Result = Reeva.evaluateScript(test262HarnessFile.readText(), realm)
//
//    Reeva.with(realm) {
//        if (test262Result.isError)
//            println("\u001b[31m[test262] ${Operations.toString(test262Result.value).string}\u001B[0m")
//    }
//
//    val result = Reeva.evaluateModule(indexFile, realm)
//
//    Reeva.with(realm) {
//        if (result.isError) {
//            println("\u001b[31m${Operations.toString(result.value).string}\u001B[0m")
//        } else if (!result.value.isUndefined) {
//            println(Operations.toPrintableString(result.value))
//        }
//    }

}
