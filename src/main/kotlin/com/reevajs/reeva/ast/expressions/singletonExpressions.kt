package com.reevajs.reeva.ast.expressions

import com.reevajs.reeva.ast.ASTNodeBase
import com.reevajs.reeva.ast.ExpressionNode
import com.reevajs.reeva.ast.VariableRefNode

object ImportMetaExpressionNode : ASTNodeBase(), ExpressionNode

class NewTargetNode : VariableRefNode(), ExpressionNode {
    override fun name() = "*new.target"
}
