package com.reevajs.reeva.core.lifecycle

import com.reevajs.reeva.ast.RootNode
import com.reevajs.reeva.interpreter.transformer.FunctionInfo
import java.io.File

data class Executable(
    val file: File?,
    val source: String,
    val name: String = file?.name ?: "<script>"
) {
    var rootNode: RootNode? = null

    var functionInfo: FunctionInfo? = null

    fun forInfo(info: FunctionInfo) = Executable(file, source, name).also {
        it.functionInfo = info
    }
}
