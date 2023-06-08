package com.reevajs.reeva.ast.expressions

import com.reevajs.reeva.ast.AstNodeBase
import com.reevajs.reeva.ast.VariableRefNode

object ImportMetaExpressionNode : AstNodeBase()

class NewTargetNode : VariableRefNode() {
    override fun name() = "*new.target"
}
