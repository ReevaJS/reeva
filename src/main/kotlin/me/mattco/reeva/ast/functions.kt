package me.mattco.reeva.ast

import me.mattco.reeva.ast.ASTNode.Companion.appendIndent
import me.mattco.reeva.ast.literals.StringLiteralNode
import me.mattco.reeva.ast.statements.ExpressionStatementNode
import me.mattco.reeva.ast.statements.StatementListNode

// This is an ExpressionNode so it can be passed to MemberExpressionNode
class ArgumentsNode(private val _argumentsList: ArgumentsListNode) : NodeBase(listOf(_argumentsList)), ExpressionNode {
    val arguments: List<ArgumentListEntry>
        get() = _argumentsList.argumentsList
}

class ArgumentsListNode(val argumentsList: List<ArgumentListEntry>) : NodeBase(argumentsList) {
    override fun dump(indent: Int) = buildString {
        dumpSelf(indent)
        argumentsList.forEach {
            appendIndent(indent + 1)
            append("ArgumentListEntry (isSpread=")
            append(it.isSpread)
            append(")\n")
            append(it.expression.dump(indent + 2))
        }
    }
}

data class ArgumentListEntry(val expression: ExpressionNode, val isSpread: Boolean) : NodeBase(listOf(expression)) {
    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (isSpread=")
        append(isSpread)
        append(")\n")
        append(expression.dump(indent + 1))
    }
}

// TODO: Simplify this structure a bit
class FunctionDeclarationNode(
    val identifier: BindingIdentifierNode?,
    val parameters: FormalParametersNode,
    val body: FunctionStatementList,
) : NodeBase(listOfNotNull(identifier, parameters, body)), DeclarationNode {
    override fun boundNames() = listOf(identifier?.identifierName ?: "*default")

    override fun contains(nodeName: String) = false

    override fun containsDuplicateLabels(labelSet: Set<String>) = false

    override fun containsUndefinedBreakTarget(labelSet: Set<String>) = false

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>) = false

    override fun declarationPart() = this

    override fun isConstantDeclaration() = false

    override fun lexicallyDeclaredNames() = boundNames()

    override fun lexicallyScopedDeclarations() = listOf(declarationPart())

    override fun topLevelLexicallyDeclaredNames() = emptyList<String>()

    override fun topLevelLexicallyScopedDeclarations() = emptyList<NodeBase>()

    override fun topLevelVarDeclaredNames() = boundNames()

    override fun topLevelVarScopedDeclarations() = listOf(declarationPart())

    override fun varDeclaredNames() = emptyList<String>()

    override fun varScopedDeclarations() = emptyList<NodeBase>()
}

// Also "FunctionBody" in the spec
class FunctionStatementList(val statementList: StatementListNode?) : NodeBase(listOfNotNull(statementList)) {
    override fun containsDuplicateLabels(labelSet: Set<String>) = false

    override fun containsUndefinedBreakTarget(labelSet: Set<String>) =
        statementList?.containsUndefinedBreakTarget(labelSet) ?: false

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>) =
        statementList?.containsUndefinedContinueTarget(iterationSet, labelSet) ?: false

    override fun containsUseStrict(): Boolean {
        statementList?.statements?.forEach { statement ->
            if (statement is ExpressionStatementNode) {
                val expr = statement.node
                if (expr is StringLiteralNode && expr.value == "use strict")
                    return true
            }
        }
        return false
    }

    override fun lexicallyDeclaredNames() = statementList?.topLevelLexicallyDeclaredNames() ?: emptyList()

    override fun lexicallyScopedDeclarations() = statementList?.topLevelLexicallyScopedDeclarations() ?: emptyList()

    override fun varDeclaredNames() = statementList?.topLevelVarDeclaredNames() ?: emptyList()

    override fun varScopedDeclarations() = statementList?.topLevelVarScopedDeclarations() ?: emptyList()
}

class FormalParametersNode(
    val functionParameters: FormalParameterListNode,
    val restParameter: FormalRestParameterNode?
) : NodeBase(listOfNotNull(functionParameters, restParameter)) {
    override fun boundNames(): List<String> {
        val list = restParameter?.element?.boundNames() ?: emptyList()
        return list + functionParameters.boundNames()
    }

    override fun containsExpression(): Boolean {
        if (functionParameters.parameters.isEmpty() && restParameter == null)
            return false
        if (functionParameters.containsExpression())
            return true
        return restParameter?.containsExpression() ?: false
    }

    override fun expectedArgumentCount(): Int {
        if (functionParameters.parameters.isEmpty())
            return 0
        return functionParameters.expectedArgumentCount()
    }

    override fun isSimpleParameterList(): Boolean {
        if (restParameter != null)
            return false
        return functionParameters.isSimpleParameterList()
    }
}

class FormalParameterListNode(val parameters: List<FormalParameterNode>) : NodeBase(parameters) {
    override fun boundNames() = parameters.flatMap(ASTNode::boundNames)

    override fun expectedArgumentCount(): Int {
        parameters.forEachIndexed { index, parameter ->
            if (parameter.hasInitializer())
                return index
        }
        return parameters.size
    }

    override fun hasInitializer() = parameters.any(ASTNode::hasInitializer)

    override fun isSimpleParameterList(): Boolean {
        return parameters.all(ASTNode::isSimpleParameterList)
    }
}

class FormalParameterNode(val bindingElement: BindingElementNode) : NodeBase(listOf(bindingElement))

// TODO: Patterns
class BindingElementNode(val binding: SingleNameBindingNode) : NodeBase(listOf(binding))

class BindingRestElementNode(val identifier: BindingIdentifierNode) : NodeBase(listOf(identifier))

class FormalRestParameterNode(val element: BindingRestElementNode) : NodeBase(listOf(element))

class SingleNameBindingNode(
    val identifier: BindingIdentifierNode,
    val initializer: InitializerNode?
) : NodeBase(listOfNotNull(identifier, initializer)) {
    override fun isSimpleParameterList() = initializer == null

    override fun hasInitializer() = initializer != null
}

class ReturnStatementNode(val node: ExpressionNode?) : NodeBase(listOfNotNull(node)), StatementNode
