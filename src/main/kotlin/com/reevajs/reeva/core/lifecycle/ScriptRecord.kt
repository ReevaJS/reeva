package com.reevajs.reeva.core.lifecycle

import com.reevajs.reeva.core.RunResult
import com.reevajs.reeva.core.errors.ThrowException
import com.reevajs.reeva.interpreter.NormalInterpretedFunction
import com.reevajs.reeva.parsing.ParsedSource
import com.reevajs.reeva.parsing.Parser
import com.reevajs.reeva.parsing.ParsingError
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.utils.Result
import com.reevajs.reeva.utils.expect

class ScriptRecord(val parsedSource: ParsedSource) : Executable {
    val realm by parsedSource.sourceInfo::realm
    private var cachedResult: RunResult? = null

    override fun execute(): RunResult {
        if (cachedResult != null)
            return cachedResult!!

        val sourceInfo = parsedSource.sourceInfo
        expect(!sourceInfo.type.isModule)

        return run {
            try {
                val transformedSource = Executable.transform(parsedSource)
                expect(realm == transformedSource.realm)

                val function = NormalInterpretedFunction.create(transformedSource, realm.globalEnv)
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
        fun parseScript(sourceInfo: SourceInfo): Result<ParsingError, ScriptRecord> {
            return Parser(sourceInfo).parseScript().mapValue(::ScriptRecord)
        }
    }
}