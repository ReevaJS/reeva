package me.mattco.jsthing.parser.ast.expressions

import me.mattco.jsthing.parser.ast.ASTNode

class ObjectProperty(val type: Type, val key: Expression, val value: Expression?, val isMethod: Boolean) : ASTNode() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (type: ")
        append(type.name)
        append(", isMethod: ")
        append(isMethod)
        append(")\n")
        appendIndent(indent + 1)
        append("Key:\n")
        append(key.dump(indent + 2))
        if (value != null) {
            appendIndent(indent + 1)
            append("Value:\n")
            append(value.dump(indent + 2))
        }
    }

    enum class Type {
        KeyValue,
        Getter,
        Setter,
        Spread
    }
}
