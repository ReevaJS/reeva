package me.mattco.jsthing.parser.ast.expressions

object ThisExpression : Expression() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append("\n")
    }
}
