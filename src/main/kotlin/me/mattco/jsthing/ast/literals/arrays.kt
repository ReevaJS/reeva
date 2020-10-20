package me.mattco.jsthing.ast.literals

import me.mattco.jsthing.ast.ExpressionNode
import me.mattco.jsthing.ast.NodeBase
import me.mattco.jsthing.ast.PrimaryExpressionNode

class ArrayLiteralNode : NodeBase(), PrimaryExpressionNode

object ElisionNode : NodeBase(), ExpressionNode
