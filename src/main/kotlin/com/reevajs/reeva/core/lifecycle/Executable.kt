package com.reevajs.reeva.core.lifecycle

import com.reevajs.reeva.ast.RootNode
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.interpreter.transformer.FunctionInfo
import java.io.File

data class Executable(
    val realm: Realm,
    val file: File?,
    val source: String,
    val name: String = file?.name ?: "<script>"
) {
    var rootNode: RootNode? = null

    var functionInfo: FunctionInfo? = null

    fun forInfo(info: FunctionInfo) = Executable(realm, file, source, name).also {
        it.functionInfo = info
    }
}
