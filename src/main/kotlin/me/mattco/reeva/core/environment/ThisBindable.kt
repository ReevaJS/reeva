package me.mattco.reeva.core.environment

import me.mattco.reeva.runtime.JSValue

interface ThisBindable {
    fun getThisBinding(): JSValue
}
