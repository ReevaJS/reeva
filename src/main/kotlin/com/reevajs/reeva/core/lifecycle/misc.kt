package com.reevajs.reeva.core.lifecycle

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.errors.ThrowException
import com.reevajs.reeva.parsing.ParsedSource
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.transformer.IRPrinter
import com.reevajs.reeva.transformer.IRValidator
import com.reevajs.reeva.transformer.TransformedSource
import com.reevajs.reeva.transformer.Transformer
import com.reevajs.reeva.utils.unreachable
import java.io.File
import java.net.URI

abstract class SourceInfo {
    abstract val name: String
    abstract val isModule: Boolean
    abstract val sourceText: String

    /**
     * Used to determine if two SourceInfos have the same origin. Typically, the
     * scheme relates somewhat to the origin of this SourceInfo, however the
     * contents of this URI are unimportant; they are directly compared for equality
     */
    abstract val uri: URI

    abstract fun resolveImportedSpecifier(specifier: String): URI

    override fun equals(other: Any?) = other is SourceInfo && other.uri == uri

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + isModule.hashCode()
        result = 31 * result + sourceText.hashCode()
        result = 31 * result + uri.hashCode()
        return result
    }
}

class FileSourceInfo @JvmOverloads constructor(
    private val file: File,
    override val isModule: Boolean = file.extension == "mjs",
    override val name: String = file.name,
    sourceText: String? = null
) : SourceInfo() {
    private var sourceTextBacker: String? = sourceText

    override val uri = file.toURI()

    override val sourceText: String
        get() {
            if (sourceTextBacker == null)
                sourceTextBacker = file.readText()
            return sourceTextBacker!!
        }

    override fun resolveImportedSpecifier(specifier: String): URI {
        if (specifier.startsWith('/'))
            return File(specifier).toURI()
        return File(file.parentFile, specifier).normalize().toURI()
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
) : SourceInfo() {
    override val uri = URI("literal:$name#${sourceTypeCount++}")

    override val sourceText = source

    override fun resolveImportedSpecifier(specifier: String): URI {
        // Literal sources are never modules, so we should never get here
        unreachable()
    }

    companion object {
        private var sourceTypeCount = 0
    }
}

interface Executable {
    @Throws(ThrowException::class)
    fun execute(): JSValue

    companion object {
        fun transform(parsedSource: ParsedSource): TransformedSource {
            return Transformer(parsedSource).transform().also {
                if (Agent.activeAgent.printIR) {
                    IRPrinter(it).print()
                    println('\n')
                }
                IRValidator(it.functionInfo).validate()
            }
        }
    }
}
