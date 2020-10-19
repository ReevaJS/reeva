package me.mattco.jsthing.ast.statements

import me.mattco.jsthing.ast.ASTNode
import me.mattco.jsthing.ast.BindingIdentifierNode
import me.mattco.jsthing.ast.InitializerNode
import me.mattco.jsthing.utils.stringBuilder

class VariableStatementNode(val declarations: List<VariableDeclarationNode>, type: Type) : StatementNode() {
    enum class Type {
        Var,
        Let,
        Const
    }

    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        declarations.forEach {
            append(it.dump(indent + 1))
        }
    }
}

class VariableDeclarationNode(val identifier: BindingIdentifierNode, val initializer: InitializerNode?) : ASTNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        append(identifier.dump(indent + 1))
        append(initializer?.dump(indent + 1))
    }
}
