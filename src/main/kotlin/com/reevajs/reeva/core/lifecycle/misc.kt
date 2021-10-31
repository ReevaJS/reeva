package com.reevajs.reeva.core.lifecycle

import com.reevajs.reeva.Reeva
import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.RunResult
import com.reevajs.reeva.parsing.ParsedSource
import com.reevajs.reeva.transformer.IRPrinter
import com.reevajs.reeva.transformer.IRValidator
import com.reevajs.reeva.transformer.TransformedSource
import com.reevajs.reeva.transformer.Transformer
import com.reevajs.reeva.utils.unreachable
import java.io.File
import java.util.*

interface SourceType {
    val name: String
    val isModule: Boolean

    fun resolveImportedFilePath(specifier: String): File
}

class FileSourceType(file: File) : SourceType {
    val file = file.normalize()
    override val name: String = file.name
    override val isModule = file.extension == "mjs"

    override fun resolveImportedFilePath(specifier: String): File {
        if (specifier.startsWith('/'))
            return File(specifier)
        return File(file.parentFile, specifier).normalize()
    }

    override fun equals(other: Any?): Boolean {
        return other is FileSourceType && file == other.file
    }

    override fun hashCode(): Int {
        return file.hashCode()
    }
}

data class LiteralSourceType(override val isModule: Boolean, override val name: String) : SourceType {
    override fun resolveImportedFilePath(specifier: String): File {
        // Literal sources are never modules, so we should never get here
        unreachable()
    }
}

data class ReplSourceType(
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

interface Executable {
    fun execute(): RunResult

    companion object {
        fun transform(parsedSource: ParsedSource): TransformedSource {
            return Transformer(parsedSource).transform().also {
                if (Reeva.activeAgent.printIR) {
                    IRPrinter(it).print()
                    println('\n')
                }
                IRValidator(it.functionInfo.ir).validate()
            }
        }
    }
}
