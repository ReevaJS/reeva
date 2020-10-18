package me.mattco.jsthing.runtime

import me.mattco.jsthing.JSSources
import me.mattco.jsthing.parser.Parser
import me.mattco.jsthing.runtime.contexts.ExecutionContext
import me.mattco.jsthing.runtime.environment.FunctionEnvRecord
import me.mattco.jsthing.runtime.environment.GlobalEnvRecord
import me.mattco.jsthing.runtime.values.nonprimitives.objects.Attributes
import me.mattco.jsthing.runtime.values.nonprimitives.objects.Descriptor
import me.mattco.jsthing.runtime.values.nonprimitives.objects.JSObject
import me.mattco.jsthing.utils.ecmaAssert
import me.mattco.jsthing.utils.expect
import java.io.File
import java.nio.ByteOrder

class Agent(val signifier: Any = "Agent${agentCount++}") {
    internal val littleEndian = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
    private var sources: MutableList<File>? = null
    private var mainFile: File? = null

    private val contexts = mutableListOf<ExecutionContext>()
    internal val runningContext: ExecutionContext
        get() = contexts.last()

    private val fileGlobalEnvs = mutableMapOf<File, GlobalEnvRecord>()

    private var createGlobalObjectHook = { realm: Realm -> JSGlobalObject(realm) }

    init {
        if (signifier in agentIdentifiers)
            throw IllegalArgumentException("Agent cannot have duplicate signifier $signifier")
        agentIdentifiers.add(signifier)

        // Begin InitializeHostDefinedRealm
        val realm = Realm(this)
        val context = ExecutionContext(this, realm, function = null)
        contexts.add(context)
        realm.init()

        // Begin SetRealmGlobalObject
        val globalObj = createGlobalObjectHook(realm)
        ecmaAssert(globalObj.isObject)
        realm.globalObject = globalObj
        val newGlobalEnv = GlobalEnvRecord.create(globalObj)
        realm.globalEnv = newGlobalEnv
        // End SetRealmGlobalObject

        // Begin SetDefaultGlobalBindings
        // TODO: Add all the properties here
        globalObj.defineOwnProperty(
            "globalThis",
            Descriptor(globalObj, Attributes(Attributes.WRITABLE and Attributes.CONFIGURABLE))
        )
        // End SetDefaultGlobalBindings
        // End InitializeHostDefinedRealm
    }

    fun createGlobalObject(hook: (Realm) -> JSGlobalObject) {
        createGlobalObjectHook = hook
    }

    fun addSources(sources: JSSources) {
        if (sources.sourceFiles?.isNotEmpty() == true) {
            if (this.sources == null)
                this.sources = mutableListOf()
            this.sources!!.addAll(sources.sourceFiles)
        }

        if (sources.mainFile != null) {
            if (mainFile != null)
                throw IllegalStateException("Illegal attempt to add multiple main files to agent $signifier")
            mainFile = sources.mainFile
        }
    }

    fun execute() {
        if (mainFile == null)
            TODO()

        val text = mainFile!!.readText()
        val scriptOrModule = Parser(text).parse()
        TODO("now what lol")
    }

    fun fileEnvironment(file: File): JSObject {
        val envRecord = fileGlobalEnvs[file] ?: TODO()
        return envRecord.globalThis
    }

    internal fun addContext(context: ExecutionContext) {
        contexts.add(context)
    }

    internal fun popContext() = contexts.removeLast()

    companion object {
        private var agentCount = 0
        private val agentIdentifiers = mutableSetOf<Any>()



        fun runStandalone(file: File): JSObject {
            val agent = Agent()
            val sources = JSSources(file)
            agent.addSources(sources)
            agent.execute()
            return agent.fileEnvironment(file)
        }
    }
}
