package com.reevajs.reeva.core.lifecycle

import com.reevajs.reeva.ast.ScriptNode
import com.reevajs.reeva.interpreter.transformer.FunctionInfo
import java.io.File

data class Executable(
    val file: File?,
    val source: String,
    val name: String = file?.name ?: "<script>"
) {
    // TODO: Modules
    var script: ScriptNode? = null

    var functionInfo: FunctionInfo? = null

    fun forInfo(info: FunctionInfo) = Executable(file, source, name).also {
        it.functionInfo = info
    }
}
