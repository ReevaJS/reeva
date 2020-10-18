package me.mattco.jsthing.parser.ast.expressions

import me.mattco.jsthing.utils.stringBuilder

class ObjectExpression(val properties: List<ObjectProperty>) : Expression() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append("\n")
        properties.forEach {
            append(it.dump(indent + 1))
        }
    }


}
