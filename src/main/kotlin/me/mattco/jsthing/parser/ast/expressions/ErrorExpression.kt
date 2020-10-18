package me.mattco.jsthing.parser.ast.expressions

class ErrorExpression : Expression() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        append(name)
        append("\n")
    }
}
