package com.reevajs.reeva.ast.expressions

import com.reevajs.reeva.ast.AstNode
import com.reevajs.reeva.ast.AstNodeBase
import com.reevajs.reeva.ast.AstVisitor
import com.reevajs.reeva.ast.VariableRefNode

object ImportMetaExpressionNode : AstNodeBase() {
    override val children get() = emptyList<AstNode>()

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

class NewTargetNode : VariableRefNode() {
    override val children get() = emptyList<AstNode>()

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    override fun name() = "*new.target"
}
