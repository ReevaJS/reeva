package me.mattco.reeva.ast

import me.mattco.reeva.utils.newline

open class NodeBase(override val children: List<ASTNode> = emptyList()) : ASTNode {
    override val name: String by lazy { this::class.java.simpleName }
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

    enum class Suffix {
        Yield,
        Await,
        In,
        Return,
        Tagged,
    }

    companion object {
        private const val INDENT = "    "

        fun makeIndent(indent: Int) = buildString {
            repeat(indent) {
                append(INDENT)
            }
        }

        fun StringBuilder.appendIndent(indent: Int) = append(makeIndent(indent))
    }

    /**
     * STATIC SEMANTICS
     */

    enum class AssignmentTargetType {
        Simple,
        Invalid
    }

    fun assignmentTargetType(): AssignmentTargetType {
        if (children.size != 1)
            return AssignmentTargetType.Invalid
        return children[0].assignmentTargetType()
    }

    fun boundNames(): List<String> {
        if (children.size != 1)
            return emptyList()
        return children[0].boundNames()
    }

    fun computedPropertyContains(symbol: NodeBase): Boolean {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "computedPropertyContains, and cannot be delegated")
        return children[0].computedPropertyContains(symbol)
    }

    fun contains(nodeName: String): Boolean {
        // This is the only SS that has a default implementation for every Nonterminal
        for (child in children) {
            if (child.name == nodeName)
                return true
            if (child.contains(nodeName))
                return true
        }

        return false
    }

    fun containsDuplicateLabels(labelSet: Set<String>): Boolean {
        if (children.size != 1)
            return false
        return children[0].containsDuplicateLabels(labelSet)
    }

    fun containsExpression(): Boolean {
        if (children.size != 1)
            return false
        return children[0].containsExpression()
    }

    fun containsUndefinedBreakTarget(labelSet: Set<String>): Boolean {
        if (children.size != 1)
            return false
        return children[0].containsUndefinedBreakTarget(labelSet)
    }

    fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>): Boolean {
        if (children.size != 1)
            return false
        return children[0].containsUndefinedContinueTarget(iterationSet, labelSet)
    }

    fun containsUseStrict(): Boolean {
        if (children.size != 1)
            return false
        return children[0].containsUseStrict()
    }

    fun coveredCallExpression(): NodeBase {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "coveredCallExpression, and cannot be delegated")
        return children[0].coveredCallExpression()
    }

    fun coveredParenthesizedExpression(): NodeBase {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "coveredParenthesizedExpression, and cannot be delegated")
        return children[0].coveredParenthesizedExpression()
    }

    fun declarationPart(): NodeBase {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "declarationPart, and cannot be delegated")
        return children[0].declarationPart()
    }

    fun expectedArgumentCount(): Int {
        if (children.size != 1)
            return 0
        return children[0].expectedArgumentCount()
    }

    fun hasInitializer(): Boolean {
        if (children.size != 1)
            return false
        return children[0].hasInitializer()
    }

    fun hasName(): Boolean {
        if (children.size != 1)
            return false
        return children[0].hasName()
    }

    fun isComputedPropertyKey(): Boolean {
        if (children.size != 1)
            return false
        return children[0].isComputedPropertyKey()
    }

    fun isConstantDeclaration(): Boolean {
        if (children.size != 1)
            return false
        return children[0].isConstantDeclaration()
    }

    fun isDestructuring(): Boolean {
        if (children.size != 1)
            return false
        return children[0].isDestructuring()
    }

    fun isFunctionDefinition(): Boolean {
        if (children.size != 1)
            return false
        return children[0].isFunctionDefinition()
    }

    fun isIdentifierRef(): Boolean {
        if (children.size != 1)
            return false
        return children[0].isIdentifierRef()
    }

    fun isLabelledFunction(): Boolean {
        if (children.size != 1)
            return false
        return children[0].isLabelledFunction()
    }

    fun isSimpleParameterList(): Boolean {
        if (children.size != 1)
            return false
        return children[0].isSimpleParameterList()
    }

    fun isStrict(): Boolean {
        if (children.size != 1)
            return false
        return children[0].isStrict()
    }

    fun isValidRegularExpressionLiteral(): Boolean {
        if (children.size != 1)
            return false
        return children[0].isValidRegularExpressionLiteral()
    }

    fun lexicallyDeclaredNames(): List<String> {
        if (children.size != 1)
            return emptyList()
        return children[0].lexicallyDeclaredNames()
    }

    fun lexicallyScopedDeclarations(): List<NodeBase> {
        if (children.size != 1)
            return emptyList()
        return children[0].lexicallyScopedDeclarations()
    }

    fun propertyNameList(): List<String> {
        if (children.size != 1)
            return emptyList()
        return children[0].propertyNameList()
    }

    fun propName(): String? {
        if (children.size != 1)
            return null
        return children[0].propName()
    }

    fun stringValue(): String {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "String, and cannot be delegated")
        return children[0].stringValue()
    }

    fun templateStrings(): List<String> {
        if (children.size != 1)
            return emptyList()
        return children[0].templateStrings()
    }

    fun topLevelLexicallyDeclaredNames(): List<String> {
        if (children.size != 1)
            return emptyList()
        return children[0].topLevelLexicallyDeclaredNames()
    }

    fun topLevelLexicallyScopedDeclarations(): List<NodeBase> {
        if (children.size != 1)
            return emptyList()
        return children[0].topLevelLexicallyScopedDeclarations()
    }

    fun topLevelVarDeclaredNames(): List<String> {
        if (children.size != 1)
            return emptyList()
        return children[0].topLevelVarDeclaredNames()
    }

    fun topLevelVarScopedDeclarations(): List<NodeBase> {
        if (children.size != 1)
            return emptyList()
        return children[0].topLevelVarScopedDeclarations()
    }

    fun varDeclaredNames(): List<String> {
        if (children.size != 1)
            return emptyList()
        return children[0].varDeclaredNames()
    }

    fun varScopedDeclarations(): List<NodeBase> {
        if (children.size != 1)
            return emptyList()
        return children[0].varScopedDeclarations()
    }
}

interface LabelledItemNode : ASTNode
interface StatementListItemNode : ASTNode
interface StatementNode : ASTNode, LabelledItemNode, StatementListItemNode // TODO: This might cause problems
interface HoistableDeclarationNode : DeclarationNode
interface DeclarationNode : StatementNode
interface BreakableStatement : StatementNode

interface ExpressionNode : ASTNode
interface LeftHandSideExpressionNode : ExpressionNode
interface ShortCircuitExpressionNode : ExpressionNode
interface MetaPropertyNode : LeftHandSideExpressionNode
interface PrimaryExpressionNode : LeftHandSideExpressionNode
interface LiteralNode : PrimaryExpressionNode

interface AssignmentPatternNode : ASTNode
interface ForBindingNode : ASTNode
interface PropertyNameNode : ASTNode
interface LiteralPropertyNameNode : ASTNode
interface TemplateLiteralNode : ASTNode
interface CatchParameterNode : ASTNode
