package me.mattco.reeva.ast.expressions

import me.mattco.reeva.ast.ASTNodeBase
import me.mattco.reeva.ast.ExpressionNode
import me.mattco.reeva.ast.VariableRefNode

object ImportMetaExpressionNode : ASTNodeBase(), ExpressionNode

class NewTargetNode : VariableRefNode(), ExpressionNode {
    override fun name() = "*new.target"
}

