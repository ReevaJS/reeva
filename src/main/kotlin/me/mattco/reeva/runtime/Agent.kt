package me.mattco.reeva.runtime

import me.mattco.reeva.ast.ASTNode
import me.mattco.reeva.compiler.ByteClassLoader
import me.mattco.reeva.compiler.Compiler
import me.mattco.reeva.compiler.TopLevelScript
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.contexts.ExecutionContext
import me.mattco.reeva.runtime.environment.EnvRecord
import me.mattco.reeva.runtime.environment.GlobalEnvRecord
import me.mattco.reeva.runtime.values.functions.JSFunction
import me.mattco.reeva.runtime.values.objects.JSObject
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList

class Agent(val signifier: Any = "Agent${objectCount++}") {
    private lateinit var globalEnv: GlobalEnvRecord
    private val classLoader = ByteClassLoader()

    init {
        if (signifier in agentIdentifiers)
            throw IllegalArgumentException("Agent cannot have duplicate signifier $signifier")
        agentIdentifiers.add(signifier)
    }

    fun execute(scriptRecord: Realm.ScriptRecord) {
        val realm = scriptRecord.realm
        val newContext = ExecutionContext(this, realm, null)
        runningContextStack.add(newContext)

        realm.initObjects()

        if (!::globalEnv.isInitialized) {
            val globalObj = JSObject.create(realm)
            realm.globalObject = globalObj
            globalEnv = GlobalEnvRecord.create(globalObj, globalObj)
            realm.globalEnv = globalEnv
        } else {
            realm.globalEnv = globalEnv
            realm.globalObject = globalEnv.globalThis
        }

        realm.populateGlobalObject()

        newContext.variableEnv = globalEnv
        newContext.lexicalEnv = globalEnv

        runningContextStack.add(newContext)

        val scriptNode = scriptRecord.scriptOrModule
        val compiler = Compiler(scriptNode, "index_js")
        val compilationResult = compiler.compile()

        val mainClassNode = compilationResult.mainClass
        val dependencies = compilationResult.dependencies
        dependencies.forEach { classNode ->
            compileClassNode(classNode, false)
        }

        val mainClass = compileClassNode(mainClassNode, true)
        val topLevelScript = mainClass.newInstance() as TopLevelScript
        topLevelScript.run(runningContext)

        runningContextStack.remove(newContext)
    }

    private fun compileClassNode(classNode: ClassNode, isMainFile: Boolean): Class<*> {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        classNode.accept(writer)
        val bytes = writer.toByteArray()

        if (EMIT_DEBUG_OUTPUT)
            FileOutputStream("./demo/out/${classNode.name}.class").use { it.write(bytes) }

        classLoader.addClass(classNode.name, bytes)
        return classLoader.loadClass(classNode.name)
    }

    companion object {
        // Used to ensure names of various things are unique
        @Volatile
        @JvmStatic
        var objectCount = 0

        private const val EMIT_DEBUG_OUTPUT = true
        private val agentIdentifiers = mutableSetOf<Any>()

        private val runningContextStack = CopyOnWriteArrayList<ExecutionContext>()

        @JvmStatic
        fun pushContext(context: ExecutionContext) {
            runningContextStack.add(context)
        }

        @JvmStatic
        fun popContext() {
            runningContextStack.removeLast()
        }

        // TODO: This is a hack for now, and doesn't really do much. However,
        // This should more-or-less be the final API. At any one time there is
        // only one running ExecutingContext, so this should be totally realizable
        @JvmStatic
        val runningContext: ExecutionContext
            get() = runningContextStack.last()
    }
}
