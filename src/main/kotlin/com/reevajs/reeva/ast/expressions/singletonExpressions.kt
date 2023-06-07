package com.reevajs.reeva.ast.expressions

import com.reevajs.reeva.ast.AstNodeBase
import com.reevajs.reeva.ast.ExpressionNode
import com.reevajs.reeva.ast.VariableRefNode

object ImportMetaExpressionNode : AstNodeBase(), ExpressionNode

class NewTargetNode : VariableRefNode(), ExpressionNode {
    override fun name() = "*new.target"
}
