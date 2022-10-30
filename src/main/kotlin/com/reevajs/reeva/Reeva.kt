package com.reevajs.reeva

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.errors.ThrowException
import com.reevajs.reeva.core.lifecycle.*
import com.reevajs.reeva.parsing.ParsingError
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.utils.Result

object Reeva {
    fun compile(
        sourceInfo: SourceInfo,
        realm: Realm = Agent.activeAgent.getActiveRealm(),
    ): Result<ParsingError, Executable> {
        return if (sourceInfo.isModule) {
            compileModule(sourceInfo, realm).cast()
        } else compileScript(sourceInfo, realm).cast()
    }

    fun compileScript(
        sourceInfo: SourceInfo,
        realm: Realm = Agent.activeAgent.getActiveRealm(),
    ): Result<ParsingError, Script> {
        return Script.parseScript(realm, sourceInfo)
    }

    fun compileModule(
        sourceInfo: SourceInfo,
        realm: Realm = Agent.activeAgent.getActiveRealm(),
    ): Result<ParsingError, ModuleRecord> {
        return SourceTextModuleRecord.parseModule(realm, sourceInfo)
    }

    fun execute(sourceInfo: SourceInfo, realm: Realm = Agent.activeAgent.getActiveRealm()): JSValue? {
        val agent = Agent.activeAgent

        val executable = compile(sourceInfo, realm)
        if (executable.hasError) {
            agent.errorReporter.reportParseError(sourceInfo, executable.error())
            return null
        }

        try {
            return executable.value().execute()
        } catch (e: ThrowException) {
            agent.errorReporter.reportRuntimeError(sourceInfo, e)
        } catch (e: Throwable) {
            agent.errorReporter.reportInternalError(sourceInfo, e)
        }

        return null
    }
}
