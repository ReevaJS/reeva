package me.mattco.jsthing.parser.ast.statements

import me.mattco.jsthing.parser.ast.Strictable
import me.mattco.jsthing.utils.stringBuilder

class FunctionDeclaration private constructor(private val data: FunctionData<String>) : Declaration(), Strictable by data {
    constructor(
        name: String,
        body: Statement,
        length: Int,
        parameters: List<FunctionData.Parameter>,
        variables: List<VariableDeclaration>,
        isStrict: Boolean
    ) : this(FunctionData(name, body, length, parameters, variables, isStrict))

    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        append(name)
        append(" (")
        append(data.name)
        append(")\n")
        if (data.parameters.isNotEmpty()) {
            appendIndent(indent + 1)
            append("Parameters (")
            append(data.length)
            append("):\n")
            append("\n")
            data.parameters.forEach {
                appendIndent(indent + 2)
                if (it.isRest)
                    append("...")
                append(it.name)
                if (it.expression != null) {
                    append(" = ")
                    append(it.expression.dump(0))
                }
                append("\n")
            }
        }
        if (data.variables.isNotEmpty()) {
            appendIndent(indent + 1)
            append("Variables:")
            append("\n")
            data.variables.forEach {
                append(it.dump(indent + 2))
            }
        }
        appendIndent(indent + 1)
        append("Body:")
        append(data.body.dump(indent + 2))
    }
}
