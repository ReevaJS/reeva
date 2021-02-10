package me.mattco.reeva.interpreter

import me.mattco.reeva.ast.ModuleNode
import me.mattco.reeva.ast.ScriptNode
import me.mattco.reeva.ast.ScriptOrModuleNode
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.core.modules.records.SourceTextModuleRecord
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.primitives.JSUndefined

class Interpreter(private val realm: Realm) {
    @Throws(ThrowException::class)
    fun interpret(scriptOrModule: ScriptOrModuleNode): JSValue {
        return JSUndefined
    }

    @Throws(ThrowException::class)
    fun interpretScript(script: ScriptNode): JSValue {
        return JSUndefined
    }

    @Throws(ThrowException::class)
    fun interpretModule(module: ModuleNode): JSValue {
        return JSUndefined
    }

    @ECMAImpl("15.2.1.17.1")
    internal fun setupModule(module: ModuleNode): SourceTextModuleRecord {
        TODO()
    }
}
