package me.mattco.jsthing.parser

import me.mattco.jsthing.parser.ast.statements.FunctionDeclaration
import me.mattco.jsthing.parser.ast.statements.VariableDeclaration

class ParserState {
    var cursor: Int = 0
    var useStrictState: UseStrictState = UseStrictState.None
    var strictMode: Boolean = false
    var inBreakContext: Boolean = false
    var inContinueContext: Boolean = false

    var errors: MutableList<ParserError> = mutableListOf()
        private set
    var varScopes: MutableList<MutableList<VariableDeclaration>> = mutableListOf()
        private set
    var letScopes: MutableList<MutableList<VariableDeclaration>> = mutableListOf()
        private set
    var functionScopes: MutableList<MutableList<FunctionDeclaration>> = mutableListOf()
        private set
    var labelsInScope: MutableSet<String> = mutableSetOf()
        private set

    fun copy() = ParserState().also {
        it.cursor = cursor
        it.useStrictState = useStrictState
        it.strictMode = strictMode

        it.errors = errors.toMutableList()
        it.varScopes = varScopes.map { it.toMutableList() }.toMutableList()
        it.letScopes = letScopes.map { it.toMutableList() }.toMutableList()
        it.functionScopes = functionScopes.map { it.toMutableList() }.toMutableList()
        it.labelsInScope = labelsInScope.toMutableSet()
    }

    enum class UseStrictState {
        None,
        Looking,
        Found
    }
}
