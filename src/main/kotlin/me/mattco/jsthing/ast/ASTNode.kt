package me.mattco.jsthing.ast

import me.mattco.jsthing.utils.newline
import me.mattco.jsthing.utils.stringBuilder

abstract class ASTNode(private val children: List<ASTNode> = emptyList()) {
    val name: String by lazy { this::class.java.simpleName }

    open fun dump(indent: Int = 0): String = stringBuilder {
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

        fun makeIndent(indent: Int) = stringBuilder {
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

    open fun assignmentTargetType(): AssignmentTargetType {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "assignmentTargetType, and cannot be delegated")
        return children[0].assignmentTargetType()
    }

    open fun boundNames(): List<String> {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "boundNames, and cannot be delegated")
        return children[0].boundNames()
    }

    open fun computedPropertyContains(symbol: ASTNode): Boolean {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "computedPropertyContains, and cannot be delegated")
        return children[0].computedPropertyContains(symbol)
    }

    open fun contains(nodeName: String): Boolean {
        // This is the only SS that has a default implementation for every Nonterminal
        for (child in children) {
            if (child.name == nodeName)
                return true
            if (child.contains(nodeName))
                return true
        }

        return false
    }

    open fun containsDuplicateLabels(): Boolean {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "containsDuplicateLabels, and cannot be delegated")
        return children[0].containsDuplicateLabels()
    }

    open fun containsExpression(): Boolean {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "containsExpression, and cannot be delegated")
        return children[0].containsExpression()
    }

    open fun containsUndefinedBreakTarget(): Boolean {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "containsUndefinedBreakTarget, and cannot be delegated")
        return children[0].containsUndefinedBreakTarget()
    }

    open fun containsUndefinedContinueTarget(): Boolean {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "containsUndefinedContinueTarget, and cannot be delegated")
        return children[0].containsUndefinedContinueTarget()
    }

    open fun coveredCallExpression(): ASTNode {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "coveredCallExpression, and cannot be delegated")
        return children[0].coveredCallExpression()
    }

    open fun coveredParenthesizedExpression(): ASTNode {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "coveredParenthesizedExpression, and cannot be delegated")
        return children[0].coveredParenthesizedExpression()
    }

    open fun declarationPart(): ASTNode {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation " +
                "), and cannot be delegated")
        return children[0].declarationPart()
    }

    open fun hasInitializer(): Boolean {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation " +
                ":, and cannot be delegated")
        return children[0].hasInitializer()
    }

    open fun hasName(): Boolean {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "Boolean, and cannot be delegated")
        return children[0].hasName()
    }

    open fun isComputedPropertyKey(): Boolean {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "isComputedPropertyKey, and cannot be delegated")
        return children[0].isComputedPropertyKey()
    }

    open fun isConstantDeclaration(): Boolean {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "isConstantDeclaration, and cannot be delegated")
        return children[0].isConstantDeclaration()
    }

    open fun isDestructuring(): Boolean {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation " +
                "), and cannot be delegated")
        return children[0].isDestructuring()
    }

    open fun isFunctionDefinition(): Boolean {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "isFunctionDefinition, and cannot be delegated")
        return children[0].isFunctionDefinition()
    }

    open fun isIdentifierRef(): Boolean {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation " +
                "), and cannot be delegated")
        return children[0].isIdentifierRef()
    }

    open fun isLabelledFunction(): Boolean {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "isLabelledFunction, and cannot be delegated")
        return children[0].isLabelledFunction()
    }

    open fun isSimpleParameterList(): Boolean {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "isSimpleParameterList, and cannot be delegated")
        return children[0].isSimpleParameterList()
    }

    open fun isStrict(): Boolean {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "Boolean, and cannot be delegated")
        return children[0].isStrict()
    }

    open fun isValidRegularExpressionLiteral(): Boolean {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "isValidRegularExpressionLiteral, and cannot be delegated")
        return children[0].isValidRegularExpressionLiteral()
    }

    open fun lexicallyDeclaredNames(): List<String> {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "lexicallyDeclaredNames, and cannot be delegated")
        return children[0].lexicallyDeclaredNames()
    }

    open fun lexicallyScopedDeclarations(): List<ASTNode> {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "lexicallyScopedDeclarations, and cannot be delegated")
        return children[0].lexicallyScopedDeclarations()
    }

    open fun propertyNameList(): List<String> {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation " +
                "(, and cannot be delegated")
        return children[0].propertyNameList()
    }

    open fun propName(): String? {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "String, and cannot be delegated")
        return children[0].propName()
    }

    open fun stringValue(): String {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "String, and cannot be delegated")
        return children[0].stringValue()
    }

    open fun templateStrings(): List<String> {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation " +
                "), and cannot be delegated")
        return children[0].templateStrings()
    }

    open fun topLevelLexicallyDeclaredNames(): List<String> {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "topLevelLexicallyDeclaredNames, and cannot be delegated")
        return children[0].topLevelLexicallyDeclaredNames()
    }

    open fun topLevelLexicallyScopedDeclarations(): List<ASTNode> {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "topLevelLexicallyScopedDeclarations, and cannot be delegated")
        return children[0].topLevelLexicallyScopedDeclarations()
    }

    open fun topLevelVarDeclaredNames(): List<String> {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "topLevelVarDeclaredNames, and cannot be delegated")
        return children[0].topLevelVarDeclaredNames()
    }

    open fun topLevelVarScopedDeclarations(): List<ASTNode> {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "topLevelVarScopedDeclarations, and cannot be delegated")
        return children[0].topLevelVarScopedDeclarations()
    }

    open fun varDeclaredNames(): List<String> {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation " +
                "(, and cannot be delegated")
        return children[0].varDeclaredNames()
    }

    open fun varScopedDeclarations(): List<ASTNode> {
        if (children.size != 1)
            throw Error("Node ${this::class.java.simpleName} has no implementation for " +
                "varScopedDeclarations, and cannot be delegated")
        return children[0].varScopedDeclarations()
    }
}

