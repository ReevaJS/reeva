package com.reevajs.reeva.ast

import com.reevajs.reeva.ast.literals.MethodDefinitionNode
import com.reevajs.reeva.parsing.Scope
import com.reevajs.reeva.parsing.lexer.SourceLocation
import com.reevajs.reeva.parsing.lexer.Token
import com.reevajs.reeva.parsing.lexer.TokenLocation
import com.reevajs.reeva.utils.newline
import kotlin.reflect.KClass

interface AstNode {
    val children: List<AstNode>
    val sourceLocation: SourceLocation

    fun accept(visitor: AstVisitor)

    // Nicely removes the extra indentation lines
    fun debugPrint() {
        val string = dump()
        val lines = string.lines().dropLastWhile { it.isBlank() }
        val lastLine = lines.last()

        val indicesToCheck = mutableListOf<Int>()
        val newLines = mutableListOf<String>()

        for ((index, ch) in lastLine.withIndex()) {
            if (ch == '|') {
                indicesToCheck.add(index)
            } else if (ch == ' ') {
                continue
            } else break
        }

        lines.asReversed().forEach { line ->
            indicesToCheck.toList().forEach { index ->
                if (line[index] != '|')
                    indicesToCheck.remove(index)
            }

            val mappedLine = line.indices.map {
                if (it in indicesToCheck) ' ' else line[it]
            }.joinToString(separator = "")

            newLines.add(0, mappedLine)
        }

        println(newLines.joinToString(separator = "\n"))
    }

    fun dump(indent: Int = 0): String = buildString {
        dumpSelf(indent)
        children.forEach {
            append(it.dump(indent + 1))
        }
    }

    fun StringBuilder.dumpSelf(indent: Int) {
        appendIndent(indent)
        appendName()
        newline()
    }

    fun StringBuilder.appendName() {
        append(this@AstNode::class.java.simpleName)
        append(" (")
        append(sourceLocation.start)
        append(" - ")
        append(sourceLocation.end)
        append(")")
    }

    val isInvalidAssignmentTarget: Boolean
        get() = true

    companion object {
        private const val INDENT = "| "

        fun makeIndent(indent: Int) = buildString {
            repeat(indent) {
                append(INDENT)
            }
        }

        fun StringBuilder.appendIndent(indent: Int) = append(makeIndent(indent))
    }
}

inline fun <reified T : AstNode> AstNode.childrenOfType(): List<T> {
    val nodes = mutableListOf<T>()

    accept(AstVisitor {
        if (it is T)
            nodes.add(it)
    })

    return nodes
}

inline fun <reified T : AstNode> AstNode.containsAny() = childrenOfType<T>().isNotEmpty()

fun AstNode.containsArguments(): Boolean {
    // Use an exception to abort the search early if we've found a matching reference
    class FoundArgumentsException : Throwable()

    try {
        accept(object : ClosureSkippingAstVisitor() {
            override fun visit(node: IdentifierReferenceNode) {
                if (node.refersToFunctionArguments())
                    throw FoundArgumentsException()
            }
        })
    } catch (e: FoundArgumentsException) {
        return true
    }

    return false
}

abstract class NodeWithScope(override val sourceLocation: SourceLocation) : AstNode {
    lateinit var scope: Scope
}

abstract class VariableRefNode(sourceLocation: SourceLocation) : NodeWithScope(sourceLocation) {
    lateinit var source: VariableSourceNode

    abstract fun name(): String
}

abstract class VariableSourceNode(sourceLocation: SourceLocation) : NodeWithScope(sourceLocation) {
    open var hoistedScope: Scope by ::scope

    lateinit var type: VariableType
    lateinit var mode: VariableMode

    abstract fun name(): String
}

// Variable not declared by the user, created at scope resolution time.
// The names of fake source nodes will always start with an asterisk
open class FakeSourceNode(private val name: String) : VariableSourceNode(SourceLocation.EMPTY) {
    override val children get() = emptyList<AstNode>()

    override fun accept(visitor: AstVisitor) = throw IllegalStateException()

    override fun name() = name
}

class GlobalSourceNode(name: String) : FakeSourceNode(name) {
    init {
        mode = VariableMode.Global
        type = VariableType.Var
    }

    override val children get() = emptyList<AstNode>()
}

sealed class RootNode(sourceLocation: SourceLocation) : NodeWithScope(sourceLocation)

class ScriptNode(
    val statements: List<AstNode>,
    val hasUseStrict: Boolean,
    sourceLocation: SourceLocation,
) : RootNode(sourceLocation) {
    override val children get() = statements

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

enum class VariableMode {
    Declared,
    Parameter,
    Global,
    Import,
}

enum class VariableType {
    Var,
    Let,
    Const
}
