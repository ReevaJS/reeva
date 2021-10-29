package com.reevajs.reeva.core

import com.reevajs.reeva.Reeva
import com.reevajs.reeva.ast.*
import com.reevajs.reeva.core.errors.ThrowException
import com.reevajs.reeva.core.lifecycle.SourceInfo
import com.reevajs.reeva.interpreter.GeneratorInterpretedFunction
import com.reevajs.reeva.interpreter.Interpreter
import com.reevajs.reeva.interpreter.NormalInterpretedFunction
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.primitives.JSEmpty
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.expect
import com.reevajs.reeva.utils.key

data class RootRecord(val sourceInfo: SourceInfo, val hostDefined: Any?) {
    val realm: Realm get() = sourceInfo.realm
}

sealed interface RootExecutable {
    val rootRecord: RootRecord

    fun evaluate(): RunResult
}

// Inherits from JSValue only for symmetry with ModuleRecord
class ScriptRecord(override val rootRecord: RootRecord) : JSValue(), RootExecutable {
    private var cachedResult: RunResult? = null

    override fun evaluate(): RunResult {
        if (cachedResult != null)
            return cachedResult!!

        val agent = Reeva.activeAgent
        val realm = rootRecord.realm
        val sourceInfo = rootRecord.sourceInfo
        expect(!rootRecord.sourceInfo.type.isModule)

        return run {
            val parseResult = agent.parse(sourceInfo)
            if (parseResult.hasError)
                return@run RunResult.ParseError(sourceInfo, parseResult.error())

            val transformedSource = agent.transform(parseResult.value())
            expect(realm == transformedSource.realm)

            try {
                val function = NormalInterpretedFunction.create(transformedSource, realm.globalEnv)
                RunResult.Success(sourceInfo, Operations.call(realm, function, realm.globalObject, emptyList()))
            } catch (e: ThrowException) {
                RunResult.RuntimeError(sourceInfo, e)
            }
        }.also {
            cachedResult = it
        }
    }
}

// Needs to inherit from JSValue because it is passed directly to parent
// modules as an argument
class ModuleRecord(override val rootRecord: RootRecord) : JSValue(), RootExecutable {
    private var cachedResult: RunResult? = null

    /**
     * The list of named exports this module provides. A value of JSEmpty
     * indicates that the module has an export with that particular name, but
     * it has not been evaluated yet. This should be treated as an error at
     * the time of _use_ of that variable.
     */
    private val namedExportsBacker = mutableMapOf<String, JSValue>()

    fun getNamedExports(): Map<String, JSValue> = namedExportsBacker

    fun getNamedExport(export: String): JSValue? = namedExportsBacker[export]

    internal fun setNamedExport(export: String, value: JSValue) {
        namedExportsBacker[export] = value
    }

    override fun evaluate(): RunResult {
        if (cachedResult != null)
            return cachedResult!!

        val agent = Reeva.activeAgent
        val realm = rootRecord.realm
        val sourceInfo = rootRecord.sourceInfo
        expect(rootRecord.sourceInfo.type.isModule)

        return run {
            val parseResult = agent.parse(sourceInfo)
            if (parseResult.hasError)
                return@run RunResult.ParseError(sourceInfo, parseResult.error())

            val parsedSource = parseResult.value()
            expect(parsedSource.node is ModuleNode)
            val exports = parsedSource.node.body.filterIsInstance<ExportNode>()

            // TODO: It is impossible to figure out all of the exports names
            //       from the AST alone, primarily because of namespace
            //       exports. We will definitely need an intermediate linking
            //       stage, similar to what the spec does
            val exportedNames = exports.flatMap {
                when (it) {
                    is DefaultClassExportNode,
                    is DefaultExpressionExportNode,
                    is DefaultFunctionExportNode -> listOf(DEFAULT_SPECIFIER)
                    is ExportNamedFromNode -> it.exports.exports.map { it.alias?.processedName ?: it.identifierNode.processedName }
                    is NamedExport -> listOf(it.alias?.processedName ?: it.identifierNode.processedName)
                    is NamedExports -> it.exports.map { it.alias?.processedName ?: it.identifierNode.processedName }
                    else -> TODO()
                }
            }

            for (name in exportedNames)
                setNamedExport(name, JSEmpty)

            val transformedSource = agent.transform(parseResult.value())

            try {
                // TODO: Avoid the JS runtime here
                val function = GeneratorInterpretedFunction.create(transformedSource, realm.globalEnv)
                val generatorObj = Operations.call(realm, function, realm.globalObject, emptyList())
                var result = Operations.invoke(realm, generatorObj, "next".key())

                while (!Operations.getV(realm, result, "done".key()).asBoolean) {
                    val moduleToImport = Operations.getV(realm, result, "value".key()).asString

                    val rootExecutable = agent.hostHooks.resolveImportedModule(rootRecord, moduleToImport)
                    expect(rootExecutable is ModuleRecord)

                    result = Operations.invoke(
                        realm,
                        generatorObj,
                        "next".key(),
                        listOf(rootExecutable),
                    )
                }

                // TODO: Module result?
                RunResult.Success(sourceInfo, JSUndefined)
            } catch (e: ThrowException) {
                RunResult.RuntimeError(sourceInfo, e)
            }
        }.also {
            cachedResult = it
        }
    }

    companion object {
        // This name doesn't really matter, as long as it isn't a valid
        // identifier. Might as well choose something descriptive.
        const val DEFAULT_SPECIFIER = "*default*"
    }
}
