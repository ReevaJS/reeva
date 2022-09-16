package com.reevajs.reeva.core.lifecycle

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.interpreter.NormalInterpretedFunction
import com.reevajs.reeva.parsing.ParsedSource
import com.reevajs.reeva.parsing.Parser
import com.reevajs.reeva.parsing.ParsingError
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.utils.Result
import com.reevajs.reeva.utils.expect

class Script(val realm: Realm, val parsedSource: ParsedSource) : Executable {
    var isEval: Boolean = false

    override fun execute(): JSValue {
        val sourceInfo = parsedSource.sourceInfo
        expect(!sourceInfo.isModule)

        return Agent.activeAgent.withRealm(realm, realm.globalEnv) {
            val transformedSource = Executable.transform(parsedSource, isEval)
            val function = NormalInterpretedFunction.create(transformedSource)
            val r = Operations.call(function, realm.globalObject, emptyList())
            r
        }

    }

    companion object {
        fun parseScript(realm: Realm, sourceInfo: SourceInfo): Result<ParsingError, Script> {
            return Parser(sourceInfo).parseScript().mapValue { result ->
                Script(realm, result)
            }
        }
    }
}
