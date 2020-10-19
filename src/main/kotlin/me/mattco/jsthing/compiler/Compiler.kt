package me.mattco.jsthing.compiler

import codes.som.anthony.koffee.assembleClass
import codes.som.anthony.koffee.modifiers.public
import me.mattco.jsthing.ast.ScriptNode
import org.objectweb.asm.tree.ClassNode

class Compiler(private val node: ScriptNode, fileName: String) {
    private val sanitizedFileName = fileName.replace(Regex("[^\\w]"), "_")

    fun compile(): ClassNode {
        return assembleClass(public, "TopLevel_$sanitizedFileName") {

        }
    }

    companion object {
        private var classCounter = 0
            get() = field++
    }
}

/*
function foo() {
}

{
    function foo() {}
}

export foo;
 */

/*
abstract class CompiledProgram {

    abstract fun run()

}




class TopLevel_libjs { }

class Function_foo$0_libjs { }

class Function_foo$1_libjs { }
 */
