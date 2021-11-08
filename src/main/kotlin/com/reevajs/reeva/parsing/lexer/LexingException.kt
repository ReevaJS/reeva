package com.reevajs.reeva.parsing.lexer

class LexingException(message: String, val source: TokenLocation) : Exception(message)
