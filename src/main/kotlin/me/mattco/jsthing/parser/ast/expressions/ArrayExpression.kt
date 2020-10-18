package me.mattco.jsthing.parser.ast.expressions

class ArrayExpression(val elements: List<Expression>) : Expression() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append("\n")
        elements.forEach {
            append(it.dump(indent + 1))
        }
    }
}
