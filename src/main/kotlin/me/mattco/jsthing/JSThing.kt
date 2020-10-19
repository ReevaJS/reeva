package me.mattco.jsthing

import me.mattco.jsthing.parser.Parser
import java.io.File

val outDirectory = File("./demo/out/")
val indexFile = File("./demo/index.js")

fun main(args: Array<String>) {
    val source = indexFile.readText()
    val script = Parser(source).parseScript()
    if (script == null) {
        println("null :(")
    } else {
        println(script.dump(0))
    }
//    println(program.dump())
//    val classNode = Compiler(program, "index.js").compile()
//    val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
//    classNode.accept(writer)
//    File(outDirectory, "index_js.class").writeBytes(writer.toByteArray())
}
