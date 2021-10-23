package com.reevajs.reeva.core.lifecycle

import com.reevajs.reeva.ast.ModuleNode
import com.reevajs.reeva.ast.RootNode
import com.reevajs.reeva.core.ModuleRecord
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.interpreter.transformer.FunctionInfo
import java.io.File

data class Executable(
    val realm: Realm,
    val file: File?,
    val source: String,
    val name: String = file?.name ?: "<script>"
) {
    var isModule = false
        private set

    var rootNode: RootNode? = null
        set(value) {
            field = value
            if (value is ModuleNode)
                isModule = true
        }

    var functionInfo: FunctionInfo? = null

    fun forInfo(info: FunctionInfo) = Executable(realm, file, source, name).also {
        it.functionInfo = info
    }
}
