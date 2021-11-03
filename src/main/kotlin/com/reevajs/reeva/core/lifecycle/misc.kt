package com.reevajs.reeva.core.lifecycle

import com.reevajs.reeva.Reeva
import com.reevajs.reeva.core.RunResult
import com.reevajs.reeva.parsing.ParsedSource
import com.reevajs.reeva.transformer.IRPrinter
import com.reevajs.reeva.transformer.IRValidator
import com.reevajs.reeva.transformer.TransformedSource
import com.reevajs.reeva.transformer.Transformer
import com.reevajs.reeva.utils.unreachable
import java.io.File

interface SourceInfo {
    val name: String
    val isModule: Boolean
    val sourceText: String

    fun resolveImportedFilePath(specifier: String): File
}

class FileSourceInfo @JvmOverloads constructor(
    private val file: File,
    override val isModule: Boolean = file.extension == "mjs",
    override val name: String = file.name,
    override val sourceText: String = file.readText()
) : SourceInfo {
    override fun resolveImportedFilePath(specifier: String): File {
        if (specifier.startsWith('/'))
            return File(specifier)
        return File(file.parentFile, specifier).normalize()
    }

    override fun equals(other: Any?): Boolean {
        return other is FileSourceInfo && file == other.file
    }

    override fun hashCode(): Int {
        return file.hashCode()
    }
}

data class LiteralSourceInfo(
    override val name: String,
    private val source: String,
    override val isModule: Boolean,
) : SourceInfo {
    override val sourceText = source

    override fun resolveImportedFilePath(specifier: String): File {
        // Literal sources are never modules, so we should never get here
        unreachable()
    }
}

data class ReplSourceType(
    override val name: String,
    override val sourceText: String,
    override val isModule: Boolean,
    val parentDirectory: File,
) : SourceInfo {
    override fun resolveImportedFilePath(specifier: String): File {
        if (specifier.startsWith('/'))
            return File(specifier)
        return File(parentDirectory, specifier).normalize()
    }
}

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
