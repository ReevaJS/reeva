package com.reevajs.reeva.core.lifecycle

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.RunResult
import com.reevajs.reeva.core.errors.ThrowException
import com.reevajs.reeva.interpreter.NormalInterpretedFunction
import com.reevajs.reeva.parsing.ParsedSource
import com.reevajs.reeva.parsing.Parser
import com.reevajs.reeva.parsing.ParsingError
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.utils.Result
import com.reevajs.reeva.utils.expect

class ScriptRecord(val realm: Realm, val parsedSource: ParsedSource) : Executable {
    private var cachedResult: RunResult? = null

    override fun execute(): RunResult {
        if (cachedResult != null)
            return cachedResult!!

        val sourceInfo = parsedSource.sourceInfo
        expect(!sourceInfo.isModule)

        return run {
            try {
                val transformedSource = Executable.transform(parsedSource)
                val function = NormalInterpretedFunction.create(realm, transformedSource, realm.globalEnv)
                RunResult.Success(sourceInfo, Operations.call(realm, function, realm.globalObject, emptyList()))
            } catch (e: ThrowException) {
                RunResult.RuntimeError(sourceInfo, e)
            } catch (e: Throwable) {
                RunResult.InternalError(sourceInfo, e)
            }
        }.also {
            cachedResult = it
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