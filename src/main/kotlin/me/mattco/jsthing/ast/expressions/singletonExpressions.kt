package me.mattco.jsthing.ast.expressions

import me.mattco.jsthing.ast.ASTNode
import me.mattco.jsthing.ast.expressions.ImportMetaNode.dumpSelf
import me.mattco.jsthing.utils.stringBuilder

class MetaPropertyNode(val metaProperty: ExpressionNode) : ExpressionNode(listOf(metaProperty)) {
    override fun isIdentifierRef() = false
}

object ImportMetaNode : ExpressionNode() {
    override fun assignmentTargetType(): AssignmentTargetType {
        return AssignmentTargetType.Invalid
    }
}

object NewTargetNode : ExpressionNode() {
    override fun assignmentTargetType(): AssignmentTargetType {
        return AssignmentTargetType.Invalid
    }
}

object TrueNode : ExpressionNode()

object FalseNode : ExpressionNode()
