package me.mattco.jsthing.ast.literals

import me.mattco.jsthing.ast.ASTNode
import me.mattco.jsthing.ast.expressions.ExpressionNode
import me.mattco.jsthing.utils.stringBuilder

class ObjectLiteralNode(val properties: List<PropertyDefinitionNode>) : ExpressionNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        properties.forEach {
            append(it.dump(indent + 1))
        }
    }
}

class PropertyDefinitionNode(val first: ExpressionNode, val second: ExpressionNode?, val type: Type) : ASTNode() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (")
        append(type.name)
        append(")\n")
        append(first.dump(indent + 1))
        second?.dump(indent + 1)?.also(::append)
    }

    enum class Type {
        Shorthand,
        Initializer,
        KeyValuePair,
        Method,
        Spread
    }
}
