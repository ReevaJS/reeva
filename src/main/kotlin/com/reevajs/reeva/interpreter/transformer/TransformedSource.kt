package com.reevajs.reeva.interpreter.transformer

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.lifecycle.SourceInfo

data class TransformedSource(
    val sourceInfo: SourceInfo,
    val functionInfo: FunctionInfo,
) {
    val realm: Realm
        get() = sourceInfo.realm

    fun forInfo(newInfo: FunctionInfo) = TransformedSource(sourceInfo, newInfo)
}
