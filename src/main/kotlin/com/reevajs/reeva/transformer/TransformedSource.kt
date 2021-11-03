package com.reevajs.reeva.transformer

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.lifecycle.SourceInfo

data class TransformedSource(
    val sourceInfo: SourceInfo,
    val functionInfo: FunctionInfo,
) {
    fun forInfo(newInfo: FunctionInfo) = TransformedSource(sourceInfo, newInfo)
}
