package com.reevajs.reeva.core.lifecycle

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.utils.unreachable
import java.io.File

interface SourceType {
    val name: String
    val isModule: Boolean

    fun resolveImportedFilePath(specifier: String): File
}

class FileSourceType(val file: File) : SourceType {
    override val name: String = file.name
    override val isModule = file.extension == "mjs"

    override fun resolveImportedFilePath(specifier: String): File {
        if (specifier.startsWith('/'))
            return File(specifier)
        return File(file.parentFile, specifier).normalize()
    }
}

class LiteralSourceType(override val isModule: Boolean, override val name: String) : SourceType {
    override fun resolveImportedFilePath(specifier: String): File {
        // Literal sources are never modules, so we should never get here
        unreachable()
    }
}

class ReplSourceType(
    override val isModule: Boolean,
    override val name: String,
    val parentDirectory: File,
) : SourceType {
    override fun resolveImportedFilePath(specifier: String): File {
        if (specifier.startsWith('/'))
            return File(specifier)
        return File(parentDirectory, specifier).normalize()
    }
}

data class SourceInfo(
    val realm: Realm,
    val source: String,
    val type: SourceType,
)
