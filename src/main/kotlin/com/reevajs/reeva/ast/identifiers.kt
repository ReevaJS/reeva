package com.reevajs.reeva.ast

import com.reevajs.reeva.ast.AstNode.Companion.appendIndent

/**
 * @param processedName The processed string of the identifier (with escape sequences
 *                          resolved)
 * @param rawName       The literal characters as they appeared in the source file
 */
class IdentifierNode(
    val processedName: String,
    val rawName: String = processedName,
) : AstNodeBase() {
    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (identifier=")
        append(processedName)
        append(")\n")
    }

    override fun toString() = processedName
}

class IdentifierReferenceNode(val identifierNode: IdentifierNode) : VariableRefNode() {
    val processedName: String
        get() = identifierNode.processedName

    val rawName: String
        get() = identifierNode.rawName

    override val isInvalidAssignmentTarget = false

    override fun name() = processedName

    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (identifier=")
        append(processedName)
        append(")\n")
    }

    override fun toString() = processedName
}
