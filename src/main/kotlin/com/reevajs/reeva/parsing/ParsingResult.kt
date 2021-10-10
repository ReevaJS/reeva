package com.reevajs.reeva.parsing

import com.reevajs.reeva.ast.ASTNode
import com.reevajs.reeva.ast.ScriptNode
import com.reevajs.reeva.parsing.lexer.TokenLocation

sealed class ParsingResult {
    class Success(val node: ASTNode) : ParsingResult()

    class ParseError(val reason: String, val start: TokenLocation, val end: TokenLocation) : ParsingResult()

    class InternalError(val cause: Throwable) : ParsingResult()
}
