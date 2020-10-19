package me.mattco.jsthing.ast

import me.mattco.jsthing.ast.expressions.ExpressionNode
import me.mattco.jsthing.ast.semantics.SSAssignmentTargetType
import me.mattco.jsthing.ast.semantics.SSBoundNames
import me.mattco.jsthing.ast.semantics.SSStringValue
import me.mattco.jsthing.utils.stringBuilder

class BindingIdentifierNode(val identifierName: String) : ASTNode(), SSStringValue, SSBoundNames {
    override fun stringValue() = identifierName

    override fun boundNames() = listOf(identifierName)

    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (identifier=")
        append(identifierName)
        append(")\n")
    }
}

class IdentifierNode(val identifierName: String) : ASTNode(), SSStringValue {
    override fun stringValue() = identifierName

    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (identifier=")
        append(identifierName)
        append(")\n")
    }
}

class IdentifierReferenceNode(val identifierName: String) : ExpressionNode(), SSStringValue, SSAssignmentTargetType {
    override fun stringValue() = identifierName

    override fun assignmentTargetType(): SSAssignmentTargetType.AssignmentTargetType {
        // TODO: Strict mode check
        return SSAssignmentTargetType.AssignmentTargetType.Simple
    }

    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (identifier=")
        append(identifierName)
        append(")\n")
    }
}

class LabelIdentifierNode(val identifierName: String) : ASTNode(), SSStringValue {
    override fun stringValue() = identifierName

    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (identifier=")
        append(identifierName)
        append(")\n")
    }
}
