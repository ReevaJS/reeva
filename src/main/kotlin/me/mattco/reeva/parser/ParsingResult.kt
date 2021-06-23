package me.mattco.reeva.parser

import me.mattco.reeva.ast.ScriptNode

sealed class ParsingResult {
    class Success(val script: ScriptNode) : ParsingResult()

    class ParseError(val reason: String, val start: TokenLocation, val end: TokenLocation) : ParsingResult()

    class InternalError(val cause: Throwable) : ParsingResult()
}
