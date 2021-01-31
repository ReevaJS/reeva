package me.mattco.reeva.ast

import me.mattco.reeva.ast.ASTNode.Companion.appendIndent

class BindingIdentifierNode(val identifierName: String) : VariableRefNode(), ExpressionNode {
    override fun stringValue() = identifierName

    override fun boundNames() = listOf(identifierName)

    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (identifier=")
        append(identifierName)
        append(")\n")
    }
}

class IdentifierNode(val identifierName: String) : ASTNodeBase(), ExpressionNode {
    override fun stringValue() = identifierName

    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (identifier=")
        append(identifierName)
        append(")\n")
    }
}

class IdentifierReferenceNode(val identifierName: String) : VariableRefNode(), PrimaryExpressionNode {
    override fun assignmentTargetType(): ASTNode.AssignmentTargetType {
        // TODO: Strict mode check
        return ASTNode.AssignmentTargetType.Simple
    }

    override fun stringValue() = identifierName

    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (identifier=")
        append(identifierName)
        append(")\n")
    }
}

class LabelIdentifierNode(val identifierName: String) : ASTNodeBase() {
    override fun stringValue() = identifierName

    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (identifier=")
        append(identifierName)
        append(")\n")
    }
}
