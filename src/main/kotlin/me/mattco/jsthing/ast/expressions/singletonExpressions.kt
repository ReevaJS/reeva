package me.mattco.jsthing.ast.expressions

import me.mattco.jsthing.ast.ASTNode
import me.mattco.jsthing.ast.expressions.ImportMetaNode.dumpSelf
import me.mattco.jsthing.utils.stringBuilder

class MetaPropertyNode(val metaProperty: ExpressionNode) : ExpressionNode(listOf(metaProperty))

object ImportMetaNode : ExpressionNode()

object NewTargetNode : ExpressionNode()

object TrueNode : ExpressionNode()

object FalseNode : ExpressionNode()
