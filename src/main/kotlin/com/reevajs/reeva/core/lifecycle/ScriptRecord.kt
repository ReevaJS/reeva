package com.reevajs.reeva.core.lifecycle

import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.interpreter.NormalInterpretedFunction
import com.reevajs.reeva.parsing.ParsedSource
import com.reevajs.reeva.parsing.Parser
import com.reevajs.reeva.parsing.ParsingError
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.utils.Result
import com.reevajs.reeva.utils.expect

class ScriptRecord(val realm: Realm, val parsedSource: ParsedSource) : Executable {
    override fun execute(): JSValue {
        val sourceInfo = parsedSource.sourceInfo
        expect(!sourceInfo.isModule)

        return run {
            val transformedSource = Executable.transform(parsedSource)
            val function = NormalInterpretedFunction.create(realm, transformedSource, realm.globalEnv)
            Operations.call(realm, function, realm.globalObject, emptyList())
        }
    }

    companion object {
        fun parseScript(realm: Realm, sourceInfo: SourceInfo): Result<ParsingError, ScriptRecord> {
            return Parser(sourceInfo).parseScript().mapValue { result ->
                ScriptRecord(realm, result)
            }
        }
    }
}