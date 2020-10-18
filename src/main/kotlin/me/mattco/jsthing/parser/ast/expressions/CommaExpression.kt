package me.mattco.jsthing.parser.ast.expressions

class CommaExpression(val expressions: List<Expression>) : Expression() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append("\n")
        expressions.forEach {
            it.dump(indent + 1)
        }
    }
}
