package me.mattco.reeva.test262

import me.mattco.reeva.Reeva
import me.mattco.reeva.core.Agent
import me.mattco.reeva.core.EvaluationResult
import me.mattco.reeva.runtime.toJSString
import me.mattco.reeva.runtime.toPrintableString
import java.io.File

fun main() {
    val test262 = File("./demo/test262.js").readText()
    val script = File("./demo/index.js").readText()

    Reeva.setup()

    val agent = Agent()

    Reeva.setAgent(agent)
    val realm = Reeva.makeRealm()

    when (val result = agent.run(test262, realm)) {
        is EvaluationResult.ParseFailure -> println("\u001b[31m[test262] Parse failure: ${result.value}\u001B[0m")
        is EvaluationResult.RuntimeError -> agent.withRealm(realm) {
            println("\u001b[31m[test262] ${result.value.toJSString()}\u001B[0m")
        }
    }

    agent.printIR = true

    when (val result = agent.run(script, realm)) {
        is EvaluationResult.ParseFailure -> println("\u001b[31mParse failure: ${result.value}\u001B[0m")
        is EvaluationResult.RuntimeError -> agent.withRealm(realm) {
            println("\u001b[31m${result.value.toJSString()}\u001B[0m")
        }
        else -> println(result.value.toPrintableString())
    }

    Reeva.teardown()
}

// PARSER BENCHMARK
//// https://gist.github.com/olegcherr/b62a09aba1bff643a049
//fun simpleMeasureTest(
//    ITERATIONS: Int = 1000,
//    TEST_COUNT: Int = 10,
//    WARM_COUNT: Int = 2,
//    callback: () -> Unit
//) {
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
//}
//
///**
// * Used to filter console messages easily
// */
//private val PRINT_REFIX = "[TimeTest]"
//
//fun main() {
//    val source = File("./demo/index.js").readText()
//
//    try {
////        simpleMeasureTest(30, 20, 10) {
////            Parser(source).parseScript()
////        }
//        val ast = Parser(source).parseScript()
//        ast.debugPrint()
//    } catch (e: Parser.ParsingException) {
//        ErrorReporter.prettyPrintError(source, e)
//    } finally {
//        Reeva.teardown()
//    }
//}
