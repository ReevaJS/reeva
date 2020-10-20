package me.mattco.jsthing.ast.expressions

import me.mattco.jsthing.ast.ASTNode
import me.mattco.jsthing.ast.expressions.ImportMetaNode.dumpSelf
import me.mattco.jsthing.utils.stringBuilder

class MetaPropertyNode(val metaProperty: ExpressionNode) : ExpressionNode()

object ImportMetaNode : ExpressionNode()

object NewTargetNode : ExpressionNode()

object TrueNode : ExpressionNode()

object FalseNode : ExpressionNode()
