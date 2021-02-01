package me.mattco.reeva.ast

import me.mattco.reeva.ast.ASTNode.Companion.appendIndent
import me.mattco.reeva.ast.literals.StringLiteralNode
import me.mattco.reeva.ast.statements.ExpressionStatementNode
import me.mattco.reeva.ast.statements.StatementListNode

// This is an ExpressionNode so it can be passed to MemberExpressionNode
class ArgumentsNode(private val _argumentsList: ArgumentsListNode) : ASTNodeBase(listOf(_argumentsList)), ExpressionNode {
    val arguments: List<ArgumentListEntry>
        get() = _argumentsList.argumentsList
}

class ArgumentsListNode(val argumentsList: List<ArgumentListEntry>) : ASTNodeBase(argumentsList) {
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

data class ArgumentListEntry(val expression: ExpressionNode, val isSpread: Boolean) : ASTNodeBase(listOf(expression)) {
    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (isSpread=")
        append(isSpread)
        append(")\n")
        append(expression.dump(indent + 1))
    }
}

open class GenericFunctionDeclarationNode(
    val identifier: BindingIdentifierNode?,
    val parameters: FormalParametersNode,
    val body: GenericFunctionStatementList,
) : VariableSourceNode(listOfNotNull(identifier, parameters, body)), DeclarationNode

class FunctionDeclarationNode(
    identifier: BindingIdentifierNode?,
    parameters: FormalParametersNode,
    body: FunctionStatementList,
) : GenericFunctionDeclarationNode(identifier, parameters, body) {
    override fun boundNames() = listOf(identifier?.identifierName ?: "*default*")

    override fun contains(nodeName: String) = false

    override fun containsDuplicateLabels(labelSet: Set<String>) = false

    override fun containsUndefinedBreakTarget(labelSet: Set<String>) = false

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>) = false

    override fun declarationPart() = this

    override fun isConstantDeclaration() = false

    override fun lexicallyDeclaredNames() = boundNames()

    override fun lexicallyScopedDeclarations() = listOf(declarationPart())

    override fun topLevelLexicallyDeclaredNames() = emptyList<String>()

    override fun topLevelLexicallyScopedDeclarations() = emptyList<ASTNodeBase>()

    override fun topLevelVarDeclaredNames() = boundNames()

    override fun topLevelVarScopedDeclarations() = listOf(declarationPart())

    override fun varDeclaredNames() = emptyList<String>()

    override fun varScopedDeclarations() = emptyList<ASTNodeBase>()
}

class FunctionExpressionNode(
    val identifier: BindingIdentifierNode?,
    val parameters: FormalParametersNode,
    val body: FunctionStatementList,
) : NodeWithScope(listOfNotNull(identifier, parameters, body)), PrimaryExpressionNode {
    override fun contains(nodeName: String) = false

    override fun hasName() = identifier != null

    override fun isFunctionDefinition() = true
}

open class GenericFunctionStatementList(val statementList: StatementListNode?) : ASTNodeBase(listOfNotNull(statementList))

// Also "FunctionBody" in the spec
class FunctionStatementList(statementList: StatementListNode?) : GenericFunctionStatementList(statementList) {
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
    val restParameter: FunctionRestParameterNode?
) : VariableSourceNode(listOfNotNull(functionParameters, restParameter)) {
    init {
        isInlineable = false
    }

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

class FormalParameterListNode(val parameters: List<FormalParameterNode>) : ASTNodeBase(parameters) {
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

class FormalParameterNode(val bindingElement: BindingElementNode) : ASTNodeBase(listOf(bindingElement))

class FunctionRestParameterNode(val element: BindingRestElement) : ASTNodeBase(listOf(element))

class ArrowFunctionNode(
    val parameters: ASTNode, // FormalParametersNode or BindingIdentifierNode
    val body: ASTNode, // Expression or FunctionStatementList
) : NodeWithScope(listOf(parameters, body)), ExpressionNode {
    override fun contains(nodeName: String): Boolean {
        if (nodeName !in listOf("NewTargetExpressionNode", "SuperPropertyExpressionNode", "SuperCallExpressionNode", "ThisLiteralNode", "super"))
            return false
        if (parameters.contains(nodeName))
            return true
        return body.contains(nodeName)
    }

    override fun containsExpression(): Boolean {
        if (parameters is BindingIdentifierNode)
            return false
        return parameters.containsExpression()
    }

    override fun containsUseStrict(): Boolean {
        if (body is ExpressionNode)
            return false
        return super<NodeWithScope>.containsUseStrict()
    }

    override fun expectedArgumentCount(): Int {
        if (parameters is BindingIdentifierNode)
            return 1
        return parameters.expectedArgumentCount()
    }

    override fun hasName() = false

    override fun isFunctionDefinition() = true

    override fun isSimpleParameterList(): Boolean {
        if (parameters is BindingIdentifierNode)
            return true
        return parameters.isSimpleParameterList()
    }

    override fun lexicallyDeclaredNames(): List<String> {
        if (body is ExpressionNode)
            return emptyList()
        return body.lexicallyDeclaredNames()
    }

    override fun lexicallyScopedDeclarations(): List<ASTNodeBase> {
        if (body is ExpressionNode)
            return emptyList()
        return body.lexicallyScopedDeclarations()
    }

    override fun varDeclaredNames(): List<String> {
        if (body is ExpressionNode)
            return emptyList()
        return body.varDeclaredNames()
    }

    override fun varScopedDeclarations(): List<ASTNodeBase> {
        if (body is ExpressionNode)
            return emptyList()
        return body.varScopedDeclarations()
    }
}

class ArrowParameters(parameters: List<CPEAPPLPart>) : ASTNodeBase()
