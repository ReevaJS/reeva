package me.mattco.reeva.ast

import me.mattco.reeva.ast.literals.StringLiteralNode
import me.mattco.reeva.ast.statements.ExpressionStatementNode
import me.mattco.reeva.ir.Scope
import me.mattco.reeva.ir.Variable
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.newline
import me.mattco.reeva.utils.unreachable
import kotlin.reflect.KClass

open class ASTNodeBase(override val children: List<ASTNode> = emptyList()) : ASTNode {
    override val name: String
        get() = this::class.java.simpleName
}

open class NodeWithScope(children: List<ASTNode> = emptyList()) : ASTNodeBase(children) {
    lateinit var scope: Scope
}

open class VariableRefNode(children: List<ASTNode> = emptyList()) : NodeWithScope(children) {
    // Either a function param, lexical decl, var decl, or
    // Script/ModuleNode (for global declarations)
    lateinit var source: ASTNode
}

abstract class VariableSourceNode(children: List<ASTNode> = emptyList()) : NodeWithScope(children) {
    val variables = mutableMapOf<String, Variable>()
    open val isConst: Boolean = false

    abstract fun boundNames(): List<String>
}

interface ASTNode {
    val name: String
    val children: List<ASTNode>

    fun dump(indent: Int = 0): String = buildString {
        dumpSelf(indent)
        children.forEach {
            append(it.dump(indent + 1))
        }
    }

    fun StringBuilder.dumpSelf(indent: Int) {
        appendIndent(indent)
        append(name)
        newline()
    }

    fun StringBuilder.appendName() = append(name)

    companion object {
        private const val INDENT = "|   "

        fun makeIndent(indent: Int) = buildString {
            repeat(indent) {
                append(INDENT)
            }
        }

        fun StringBuilder.appendIndent(indent: Int) = append(makeIndent(indent))
    }

    val isInvalidAssignmentTarget: Boolean
        get() = true

    fun scopedVariableDeclarations(): List<VariableSourceNode> {
        return children.flatMap(ASTNode::scopedVariableDeclarations)
    }

    fun scopedLexicalDeclarations(): List<VariableSourceNode> {
        return children.flatMap(ASTNode::scopedLexicalDeclarations)
    }

    fun variableDeclarations(): List<VariableSourceNode> {
        return children.flatMap(ASTNode::variableDeclarations)
    }

    fun lexicalDeclarations(): List<VariableSourceNode> {
        return children.flatMap(ASTNode::lexicalDeclarations)
    }

    fun declaredVarNames(): List<String> = variableDeclarations().flatMap(VariableSourceNode::boundNames)

    fun declaredLexNames(): List<String> = lexicalDeclarations().flatMap(VariableSourceNode::boundNames)

    fun containsDirective(directive: String) = children.containsDirective(directive)

    fun containsUseStrictDirective() = children.containsUseStrictDirective()
}

fun List<ASTNode>.containsDirective(directive: String) = isNotEmpty() && first().let { stmt ->
    stmt is ExpressionStatementNode && stmt.node.let {
        it is StringLiteralNode && it.value == directive
    }
}

fun List<ASTNode>.containsUseStrictDirective() = containsDirective("use strict")

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
        get() = isModule || asScript.containsUseStrictDirective()

    fun dump(n: Int = 0) = when {
        isScript -> asScript.dump(n)
        isModule -> asModule.dump(n)
        else -> unreachable()
    }
}

interface StatementNode : ASTNode
interface ExpressionNode : ASTNode
