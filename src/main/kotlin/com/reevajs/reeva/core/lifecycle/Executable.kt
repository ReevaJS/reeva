package com.reevajs.reeva.core.lifecycle

import com.reevajs.reeva.ast.ScriptNode
import com.reevajs.reeva.interpreter.transformer.IRPackage
import java.io.File

data class Executable(
    val file: File?,
    val source: String,
    val name: String = file?.name ?: "<anonymous>"
) {
    // TODO: Modules
    var script: ScriptNode? = null

    var ir: IRPackage? = null
}
