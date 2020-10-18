package me.mattco.jsthing.parser.ast.expressions

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
