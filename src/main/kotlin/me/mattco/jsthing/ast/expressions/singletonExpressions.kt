package me.mattco.jsthing.ast.expressions

import me.mattco.jsthing.ast.*

object ImportMetaNode : NodeBase(), MetaPropertyNode {
    override fun assignmentTargetType(): ASTNode.AssignmentTargetType {
        return ASTNode.AssignmentTargetType.Invalid
    }
}

object NewTargetNode : NodeBase(), MetaPropertyNode {
    override fun assignmentTargetType(): ASTNode.AssignmentTargetType {
        return ASTNode.AssignmentTargetType.Invalid
    }
}

object TrueNode : NodeBase(), LiteralNode

object FalseNode : NodeBase(), LiteralNode
