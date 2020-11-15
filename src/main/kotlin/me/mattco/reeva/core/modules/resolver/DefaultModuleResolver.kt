package me.mattco.reeva.core.modules.resolver

import me.mattco.reeva.Reeva
import me.mattco.reeva.ast.ScriptOrModuleNode
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.modules.records.JVMPackageModuleRecord
import me.mattco.reeva.core.modules.records.ModuleRecord
import me.mattco.reeva.interpreter.Interpreter
import me.mattco.reeva.parser.Parser
import java.io.File

class DefaultModuleResolver(
    private val realm: Realm,
    private val relativeBase: File,
    private val absoluteSources: List<File>,
) : ModuleResolver() {
    constructor(realm: Realm, relativeBase: File, absoluteSource: File) : this(realm, relativeBase, listOf(absoluteSource))
    constructor(realm: Realm, relativeBase: File) : this(realm, relativeBase, emptyList())

    override fun resolve(path: String): ModuleRecord? {
        if (path.startsWith("./")) {
            val file = File(relativeBase, path.substring(2))
            if (file.exists()) {
                return resolveJSModule(file)
            }

            return null
        }

        if (path.startsWith("jvm:")) {
            return resolveJVMModule(path.substring(4))
        }

        absoluteSources.forEach { absoluteSource ->
            val file = File(absoluteSource, path)
            if (file.exists()) {
                return resolveJSModule(file)
            }
        }

        return null
    }

    private fun resolveJSModule(file: File): ModuleRecord {
        val module = Parser(file.readText(), realm).parseModule()
        if (Reeva.PRINT_PARSE_NODES) {
            println("==== module ${file.absolutePath} ====")
            println(module.dump(1))
        }
        return Interpreter(realm, ScriptOrModuleNode(module)).setupModule()
    }

    private fun resolveJVMModule(packageName: String): ModuleRecord {
        return JVMPackageModuleRecord(realm, null, packageName)
    }
}
