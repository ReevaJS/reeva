package com.reevajs.reeva.core.lifecycle

import com.reevajs.reeva.core.Realm
import java.io.File

abstract class SourceType {
    abstract val name: String
    abstract val isModule: Boolean
}

class FileSourceType(val file: File) : SourceType() {
    override val name: String = file.name
    override val isModule = file.extension == "mjs"
}

class LiteralSourceType(override val isModule: Boolean, override val name: String) : SourceType()

data class SourceInfo(
    val realm: Realm,
    val source: String,
    val type: SourceType,
)
