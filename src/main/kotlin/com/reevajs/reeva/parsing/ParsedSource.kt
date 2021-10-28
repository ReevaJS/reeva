package com.reevajs.reeva.parsing

import com.reevajs.reeva.ast.NodeWithScope
import com.reevajs.reeva.core.lifecycle.SourceInfo
import com.reevajs.reeva.parsing.lexer.TokenLocation

data class ParsedSource(
    val sourceInfo: SourceInfo,
    val node: NodeWithScope,
)

class ParsingError(val cause: String, val start: TokenLocation, val end: TokenLocation)
