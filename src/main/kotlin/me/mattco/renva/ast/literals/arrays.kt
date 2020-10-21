package me.mattco.renva.ast.literals

import me.mattco.renva.ast.ExpressionNode
import me.mattco.renva.ast.NodeBase
import me.mattco.renva.ast.PrimaryExpressionNode

class ArrayLiteralNode : NodeBase(), PrimaryExpressionNode

object ElisionNode : NodeBase(), ExpressionNode
