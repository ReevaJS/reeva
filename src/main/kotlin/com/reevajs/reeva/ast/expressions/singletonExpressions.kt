package com.reevajs.reeva.ast.expressions

import com.reevajs.reeva.ast.AstNode
import com.reevajs.reeva.ast.AstNodeBase
import com.reevajs.reeva.ast.VariableRefNode

object ImportMetaExpressionNode : AstNodeBase() {
    override val children get() = emptyList<AstNode>()
}

class NewTargetNode : VariableRefNode() {
    override val children get() = emptyList<AstNode>()

    override fun name() = "*new.target"
}
