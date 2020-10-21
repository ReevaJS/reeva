package me.mattco.reeva.ast.literals

import me.mattco.reeva.ast.ExpressionNode
import me.mattco.reeva.ast.NodeBase
import me.mattco.reeva.ast.PrimaryExpressionNode

class ArrayLiteralNode : NodeBase(), PrimaryExpressionNode

object ElisionNode : NodeBase(), ExpressionNode
