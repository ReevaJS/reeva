package me.mattco.jsthing.parser.ast.statements

import me.mattco.jsthing.parser.ast.Strictable
import me.mattco.jsthing.parser.ast.expressions.Expression

class FunctionData<NameType>(
    val name: NameType,
    val body: Statement,
    val length: Int,
    val parameters: List<Parameter>,
    val variables: List<VariableDeclaration>,
    override val isStrict: Boolean
) : Strictable {
    data class Parameter(
        val name: String,
        val expression: Expression?,
        val isRest: Boolean
    )
}
