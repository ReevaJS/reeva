package me.mattco.reeva.parsing

import me.mattco.reeva.ast.ASTNode

sealed class ParsingResult {
    class Success(val node: ASTNode) : ParsingResult()

    class ParseError(val reason: String, val start: TokenLocation, val end: TokenLocation) : ParsingResult()

    class InternalError(val cause: Throwable) : ParsingResult()
}
