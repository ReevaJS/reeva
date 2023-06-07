package com.reevajs.reeva.ast

import com.reevajs.reeva.ast.literals.MethodDefinitionNode
import com.reevajs.reeva.ast.statements.StatementList
import com.reevajs.reeva.parsing.Scope
import com.reevajs.reeva.parsing.lexer.SourceLocation
import com.reevajs.reeva.parsing.lexer.Token
import com.reevajs.reeva.parsing.lexer.TokenLocation
import com.reevajs.reeva.utils.newline
import kotlin.reflect.KClass

open class AstNodeBase(children: List<AstNode> = emptyList()) : AstNode {
    override lateinit var parent: AstNode

    final override val children: MutableList<AstNode> = if (children is ArrayList<*>) {
        children as MutableList<AstNode>
    } else children.toMutableList()

    override val astNodeName: String
        get() = this::class.java.simpleName

    final override var sourceLocation = SourceLocation(TokenLocation.EMPTY, TokenLocation.EMPTY)

    init {
        children.forEach { it.parent = this }

        if (children.size == 1)
            sourceLocation = children.first().sourceLocation
    }
}

fun <T : AstNode> T.withPosition(start: TokenLocation, end: TokenLocation) = apply {
    sourceLocation = SourceLocation(start, end)
}

fun <T : AstNode> T.withPosition(sourceLocation: SourceLocation) = apply {
    this.sourceLocation = sourceLocation
}

fun <T : AstNode> T.withPosition(token: Token) = withPosition(token.start, token.end)

fun <T : AstNode> T.withPosition(node: AstNode) = withPosition(node.sourceLocation)

fun <T : AstNode> T.withPosition(start: AstNode, end: AstNode) = withPosition(
    start.sourceLocation.start,
    end.sourceLocation.end,
)

open class NodeWithScope(children: List<AstNode> = emptyList()) : AstNodeBase(children) {
    lateinit var scope: Scope
}

abstract class VariableRefNode(children: List<AstNode> = emptyList()) : NodeWithScope(children) {
    lateinit var source: VariableSourceNode

    abstract fun name(): String
}

abstract class VariableSourceNode(children: List<AstNode> = emptyList()) : NodeWithScope(children) {
    open var hoistedScope: Scope by ::scope

    var isInlineable = true

    lateinit var key: VariableKey

    lateinit var type: VariableType
    lateinit var mode: VariableMode

    abstract fun name(): String
}

// Represents the way a variable is stored during execution
sealed interface VariableKey {
    // The variable is stored directly in the interpreter's locals list, and
    // accessed directly by the given index
    class InlineIndex(val index: Int) : VariableKey

    // The variable is stored in a non-optimized DeclarativeEnvRecord and
    // accessed by its name
    object Named : VariableKey
}

// Variable not declared by the user, created at scope resolution time.
// The names of fake source nodes will always start with an asterisk
open class FakeSourceNode(private val name: String) : VariableSourceNode() {
    override fun name() = name
}

class GlobalSourceNode(name: String) : FakeSourceNode(name) {
    init {
        mode = VariableMode.Global
        type = VariableType.Var
    }
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

interface AstNode {
    val astNodeName: String
    val children: MutableList<AstNode>

    var sourceLocation: SourceLocation

    var parent: AstNode

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
        append(astNodeName)
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

fun <T : Any> childrenOfTypeHelper(node: AstNode, clazz: KClass<T>, list: MutableList<T>) {
    node.children.forEach {
        if (it::class == clazz) {
            @Suppress("UNCHECKED_CAST")
            list.add(it as T)
        }
        childrenOfTypeHelper(it, clazz, list)
    }
}

inline fun <reified T : Any> AstNode.childrenOfType(): List<T> {
    return mutableListOf<T>().also { childrenOfTypeHelper(this, T::class, it) }
}

inline fun <reified T : Any> AstNode.containsAny() = childrenOfType<T>().isNotEmpty()

fun AstNode.containsArguments(): Boolean {
    val idents = childrenOfType<IdentifierReferenceNode>()
    if (idents.any { it.rawName == "arguments" })
        return true

    for (node in children) {
        when (node) {
            is FunctionDeclarationNode -> return false
            is MethodDefinitionNode -> if (node.propName.containsArguments()) {
                return true
            }
            else -> if (node.containsArguments()) {
                return true
            }
        }
    }

    return false
}

sealed class RootNode(children: List<AstNode>) : NodeWithScope(children)

class ScriptNode(val statements: StatementList, val hasUseStrict: Boolean) : RootNode(statements)

interface StatementNode : AstNode

interface ExpressionNode : AstNode
