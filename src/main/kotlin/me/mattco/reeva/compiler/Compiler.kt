package me.mattco.reeva.compiler

import codes.som.anthony.koffee.labels.LabelLike
import me.mattco.reeva.ast.ModuleNode
import me.mattco.reeva.ast.ScriptNode

open class Compiler {
    protected var stackHeight = 0
    protected val labelNodes = mutableListOf<LabelNode>()
    protected val dependencies = mutableListOf<NamedByteArray>()

    data class LabelNode(
        val stackHeight: Int,
        val labelName: String?,
        val breakLabel: LabelLike?,
        val continueLabel: LabelLike?
    )

    data class CompilationResult(
        val primary: NamedByteArray,
        val dependencies: List<NamedByteArray>,
    )

    data class NamedByteArray(val name: String, val bytes: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other)
                return true
            return other is NamedByteArray && name == other.name && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + bytes.contentHashCode()
            return result
        }
    }

    fun compileScript(script: ScriptNode): CompilationResult {
        return CompilationResult(NamedByteArray("", ByteArray(0)), emptyList())
    }

    fun compileModule(module: ModuleNode): CompilationResult {
        return CompilationResult(NamedByteArray("", ByteArray(0)), emptyList())
    }
}
