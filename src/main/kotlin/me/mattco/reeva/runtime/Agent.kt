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
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList

class Agent(val signifier: Any = "Agent${agentCount++}") {
    private lateinit var globalEnv: GlobalEnvRecord
    private val classLoader = ByteClassLoader()

    init {
        if (signifier in agentIdentifiers)
            throw IllegalArgumentException("Agent cannot have duplicate signifier $signifier")
        agentIdentifiers.add(signifier)
    }

    fun execute(scriptRecord: Realm.ScriptRecord) {
        val realm = scriptRecord.realm
        val newContext = ExecutionContext(this, realm, null, scriptRecord.scriptOrModule)
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
        val classNode = compiler.compile()
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        classNode.accept(writer)
        val bytes = writer.toByteArray()

        val outFile = File("./demo/out/${classNode.name}.class")
        val out = FileOutputStream(outFile)
        out.write(bytes)
        out.close()

        val topLevelScript = instantiateClass<TopLevelScript>(compiler.className, bytes)
        topLevelScript.run(runningContext)

        runningContextStack.remove(newContext)
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> instantiateClass(name: String, bytes: ByteArray): T {
        classLoader.addClass(name, bytes)
        val instance = classLoader.loadClass(name) as Class<T>
        return instance.newInstance()
    }

    // TODO: Is there a better place for this?
    @ECMAImpl("InstantiateFunctionObject", "14.1.22")
    private fun instantiateFunctionObject(function: ASTNode, env: EnvRecord): JSFunction {
        TODO()
    }

    companion object {
        private var agentCount = 0
        private val agentIdentifiers = mutableSetOf<Any>()

        private val runningContextStack = CopyOnWriteArrayList<ExecutionContext>()

        @JvmStatic
        fun pushContext(context: ExecutionContext) {
            runningContextStack.add(context)
        }

        @JvmStatic
        fun popContext(context: ExecutionContext) {
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
