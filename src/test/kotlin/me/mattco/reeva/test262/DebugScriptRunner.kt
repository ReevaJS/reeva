package me.mattco.reeva.test262

import me.mattco.reeva.Reeva
import me.mattco.reeva.core.Agent
import me.mattco.reeva.interpreter.ExecutionResult
import me.mattco.reeva.runtime.toPrintableString
import java.io.File
import kotlin.math.max

val test262Helpers = listOf(
    "assert.js",
    "sta.js",
    "propertyHelper.js",
)

fun main() {
    Reeva.setup()

    val agent = Agent()
    Reeva.setAgent(agent)
    val realm = Reeva.makeRealm()

//     val test262Script = collectTest262Script()
//     val test262Result = agent.run(test262Script, realm)
//     if (test262Result.isError) {
//         println(test262Result)
//         return
//     }

    agent.printIR = true

    val script = File("./demo/index.js").readText()
    val result = agent.run(script, realm)

    if (result.isError) {
        printError(result, script)
    } else {
        println("Script result: ${(result as ExecutionResult.Success).result.toPrintableString()}")
    }

    Reeva.teardown()
}

fun printError(result: ExecutionResult, script: String) {
    if (result is ExecutionResult.ParseError) {
        val reason = result.reason
        val start = result.start
        val end = result.end

        val lines = script.lines()
        val firstLine = (start.line - 2).coerceAtLeast(0)
        val lastLine = (start.line + 2).coerceAtMost(lines.lastIndex)

        val lineIndexWidth = max(firstLine.toString().length, lastLine.toString().length)

        for (i in firstLine..lastLine) {
            if (lines[i].isBlank())
                continue

            print("\u001b[2;37m%${lineIndexWidth}d:    \u001b[0m".format(i))
            println(lines[i])
            if (i == start.line) {
                print(" ".repeat(start.column + lineIndexWidth + 5))
                print("\u001b[31m")
                val numCarets = if (start.line == end.line) {
                    (end.column - start.column).coerceAtMost(lines[i].length)
                } else lines[i].length - start.column
                println("^".repeat(numCarets))
                print("\u001b[0m")
            }
        }

        println()
        println("\u001b[31mSyntaxError: $reason\u001b[0m")
    } else {
        println(result.toString())
    }
}

private fun collectTest262Script(): String {
    return buildString {
        test262Helpers.forEach {
            append(File(Test262Runner.harnessDirectory, it).readText())
            append('\n');
        }
    }
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
