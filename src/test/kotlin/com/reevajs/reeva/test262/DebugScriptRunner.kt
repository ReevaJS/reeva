package com.reevajs.reeva.test262

import com.reevajs.reeva.Reeva
import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.runtime.toPrintableString
import java.io.File
import kotlin.concurrent.thread

val test262Helpers = listOf(
    "assert.js",
    "sta.js",
    "propertyHelper.js",
)

fun main() {
    Reeva.setup()

    val agent = Agent()
    val realm = agent.makeRealm()

//     val test262Script = collectTest262Script()
//     val test262Result = agent.run(test262Script, realm)
//     if (test262Result.isError) {
//         println(test262Result)
//         return
//     }

    agent.printIR = true
    Agent.setAgent(agent)

    val file = File("./demo/index.mjs")
    val result = agent.run(realm, file)
    agent.eventLoop.blockUntilEmpty()

    result.unwrap()?.also {
        println("Script result: ${it.toPrintableString()}")
    }
}

private fun collectTest262Script(): String {
    return buildString {
        test262Helpers.forEach {
            append(File(Test262Runner.harnessDirectory, it).readText())
            append('\n')
        }
    }
}

// PARSER BENCHMARK
// // https://gist.github.com/olegcherr/b62a09aba1bff643a049
// fun simpleMeasureTest(
//    ITERATIONS: Int = 1000,
//    TEST_COUNT: Int = 10,
//    WARM_COUNT: Int = 2,
//    callback: () -> Unit
// ) {
//    val results = ArrayList<Long>()
//    var totalTime = 0L
//    var t = 0
//
//    println("$PRINT_REFIX -> go")
//
//    while (++t <= TEST_COUNT + WARM_COUNT) {
//        val startTime = System.currentTimeMillis()
//
//        var i = 0
//        while (i++ < ITERATIONS)
//            callback()
//
//        if (t <= WARM_COUNT) {
//            println("$PRINT_REFIX Warming $t of $WARM_COUNT")
//            continue
//        }
//
//        val time = System.currentTimeMillis() - startTime
//        println(PRINT_REFIX+" "+time.toString()+"ms")
//
//        results.add(time)
//        totalTime += time
//    }
//
//    val average = totalTime / TEST_COUNT
//    val median = results.sorted()[results.size / 2]
//
//    println("$PRINT_REFIX -> average=${average}ms / median=${median}ms")
// }
//
// /**
// * Used to filter console messages easily
// */
// private val PRINT_REFIX = "[TimeTest]"
//
// fun main() {
//    val source = File("./demo/index.js").readText()
//
//    try {
// //        simpleMeasureTest(30, 20, 10) {
// //            Parser(source).parseScript()
// //        }
//        val ast = Parser(source).parseScript()
//        ast.debugPrint()
//    } catch (e: Parser.ParsingException) {
//        ErrorReporter.prettyPrintError(source, e)
//    } finally {
//        Reeva.teardown()
//    }
// }
