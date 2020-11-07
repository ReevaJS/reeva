package me.mattco.reeva.core.modules

import me.mattco.reeva.Reeva
import me.mattco.reeva.ast.ScriptOrModule
import me.mattco.reeva.core.Realm
import me.mattco.reeva.interpreter.Interpreter
import me.mattco.reeva.parser.Parser
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.module.JSModuleNamespaceObject
import me.mattco.reeva.utils.ecmaAssert
import java.io.File

class ModuleResolver(val pathResolver: PathResolver) {
    lateinit var realm: Realm

    private val cachedModules = mutableMapOf<String, ModuleRecord>()

    abstract class PathResolver {
        abstract fun resolve(path: String): File?
    }

    class DefaultPathResolver(
        val relativeBase: File,
        val absoluteSource: File?,
    ) : PathResolver() {
        private val cachedFiles = mutableMapOf<String, File?>()

        override fun resolve(path: String): File? {
            if (path in cachedFiles)
                return cachedFiles[path]

            if (path.startsWith("./")) {
                val file = File(relativeBase, path.substring(2))
                if (file.exists()) {
                    cachedFiles[path] = file
                    return file
                }

                cachedFiles[path] = null
                return null
            }

            if (absoluteSource != null) {
                val file = File(absoluteSource, path)
                if (file.exists()) {
                    cachedFiles[path] = file
                    return file
                }
            }

            cachedFiles[path] = null
            return null
        }
    }

    class DefaultPathResolverMultiSource(
        val relativeBase: File,
        val absoluteSources: List<File>,
    ) : PathResolver() {
        private val cachedFiles = mutableMapOf<String, File?>()

        override fun resolve(path: String): File? {
            if (path in cachedFiles)
                return cachedFiles[path]

            if (path.startsWith("./")) {
                val file = File(relativeBase, path.substring(2))
                if (file.exists()) {
                    cachedFiles[path] = file
                    return file
                }

                cachedFiles[path] = null
                return null
            }

            absoluteSources.forEach { absoluteSource ->
                val file = File(absoluteSource, path)
                if (file.exists()) {
                    cachedFiles[path] = file
                    return file
                }
            }

            cachedFiles[path] = null
            return null
        }
    }

    @ECMAImpl("15.2.1.18")
    fun hostResolveImportedModule(referencingModule: ModuleRecord, specifier: String): ModuleRecord {
        val file = pathResolver.resolve(specifier) ?: TODO()
        return cachedModules.getOrPut(file.absolutePath) {
            val module = Parser(file.readText(), realm).parseModule()
            if (Reeva.PRINT_PARSE_NODES) {
                println("==== module ${file.absolutePath} ====")
                println(module.dump(1))
            }
            Interpreter(realm, ScriptOrModule(module)).setupModule()
        }
    }

    companion object {
        @ECMAImpl("15.2.1.21")
        fun getModuleNamespace(module: ModuleRecord): JSModuleNamespaceObject {
            if (module is CyclicModuleRecord)
                ecmaAssert(module.status != CyclicModuleRecord.Status.Unlinked)

            if (module.namespace != null)
                return module.namespace!!

            val unambiguousNames = mutableListOf<String>()
            module.getExportedNames(mutableSetOf()).forEach { name ->
                val resolution = module.resolveExport(name)
                if (resolution != null)
                    unambiguousNames.add(name)
            }

            return JSModuleNamespaceObject.create(module.realm, module, unambiguousNames)
        }
    }
}
