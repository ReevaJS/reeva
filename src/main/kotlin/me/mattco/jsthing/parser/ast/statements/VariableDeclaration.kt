package me.mattco.jsthing.parser.ast.statements

class VariableDeclaration(
    val type: Type,
    val declarations: List<VariableDeclarator>
) : Declaration() {
    override fun dump(indent: Int) = stringBuilder {
        append(makeIndent(indent))
        append(name)
        append(" (")
        append(when (type) {
            Type.Var -> "var"
            Type.Let -> "let"
            Type.Const -> "const"
        })
        append(")\n")
        declarations.forEach {
            append(it.dump(indent + 1))
        }
    }
}
