package me.mattco.reeva.ast

import me.mattco.reeva.ast.statements.StatementList
import me.mattco.reeva.parsing.Scope
import me.mattco.reeva.parsing.lexer.TokenLocation
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.newline
import me.mattco.reeva.utils.unreachable
import kotlin.reflect.KClass

open class ASTNodeBase(children: List<ASTNode> = emptyList()) : ASTNode {
    override lateinit var parent: ASTNode

    final override val children: MutableList<ASTNode> = if (children is ArrayList<*>) {
        children as MutableList<ASTNode>
    } else children.toMutableList()

    override val astNodeName: String
        get() = this::class.java.simpleName

    override var sourceStart: TokenLocation = TokenLocation.EMPTY
    override var sourceEnd: TokenLocation = TokenLocation.EMPTY

    init {
        children.forEach { it.parent = this }
    }
}

fun <T : ASTNode> T.withPosition(start: TokenLocation, end: TokenLocation) = apply {
    sourceStart = start
    sourceEnd = end
}

fun <T : ASTNode> T.withPosition(node: ASTNode) = withPosition(node.sourceStart, node.sourceEnd)

open class NodeWithScope(children: List<ASTNode> = emptyList()) : ASTNodeBase(children) {
    lateinit var scope: Scope
}

abstract class VariableRefNode(children: List<ASTNode> = emptyList()) : NodeWithScope(children) {
    lateinit var source: VariableSourceNode

    abstract fun name(): String
}

abstract class VariableSourceNode(children: List<ASTNode> = emptyList()) : NodeWithScope(children) {
    open var hoistedScope: Scope by ::scope

    var slot: Int = -1
    var isInlineable = true

    lateinit var type: VariableType
    lateinit var mode: VariableMode

    abstract fun name(): String
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
}

enum class VariableType {
    Var,
    Let,
    Const
}

interface ASTNode {
    val astNodeName: String
    val children: MutableList<ASTNode>

    var sourceStart: TokenLocation
    var sourceEnd: TokenLocation

    var parent: ASTNode

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
        append(sourceStart)
        append(" - ")
        append(sourceEnd)
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

fun <T : Any> childrenOfTypeHelper(node: ASTNode, clazz: KClass<T>, list: MutableList<T>) {
    node.children.forEach {
        if (it::class == clazz) {
            @Suppress("UNCHECKED_CAST")
            list.add(it as T)
        }
        childrenOfTypeHelper(it, clazz, list)
    }
}

inline fun <reified T : Any> ASTNode.childrenOfType(): List<T> {
    return mutableListOf<T>().also { childrenOfTypeHelper(this, T::class, it) }
}

inline fun <reified T : Any> ASTNode.containsAny() = childrenOfType<T>().isNotEmpty()

class ScriptNode(val statements: StatementList, val hasUseStrict: Boolean) : NodeWithScope(statements)

class ModuleNode(val body: StatementList) : NodeWithScope(body)

interface StatementNode : ASTNode
interface ExpressionNode : ASTNode

// TODO: Remove
class ScriptOrModuleNode(private val value: Any) {
    init {
        expect(value is ScriptNode || value is ModuleNode)
    }

    val isScript: Boolean
        get() = value is ScriptNode

    val isModule: Boolean
        get() = value is ModuleNode

    val asScript: ScriptNode
        get() = value as ScriptNode

    val asModule: ModuleNode
        get() = value as ModuleNode

    val isStrict: Boolean
        get() = isModule

    fun dump(n: Int = 0) = when {
        isScript -> asScript.dump(n)
        isModule -> asModule.dump(n)
        else -> unreachable()
    }
}
