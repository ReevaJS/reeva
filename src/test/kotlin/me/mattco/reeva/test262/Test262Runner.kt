package me.mattco.reeva.test262

import com.charleskorn.kaml.Yaml
import me.mattco.reeva.core.Agent
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ExecutionContext
import me.mattco.reeva.utils.expect
import org.junit.BeforeClass
import org.junit.Test
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class Test262Runner(private val name: String, private val script: String, private val metadata: Test262Metadata) {
    @Test
    fun test262Test() {
        Assumptions.assumeTrue(metadata.flags == null)
        Assumptions.assumeTrue(metadata.negative == null)
        Assumptions.assumeTrue(metadata.features?.any { "intl" in it.toLowerCase() } != true)

        val realm = Realm()
        val context = ExecutionContext(agent, realm, null)
        Agent.pushContext(context)
        realm.initObjects()
        val globalObject = Test262GlobalObject.create(realm)
        realm.setGlobalObject(globalObject)
        Agent.popContext()

        var requiredScript = pretestScript

        metadata.includes?.forEach { include ->
            requiredScript += '\n'
            requiredScript += File(harnessDirectory, include).readText()
        }

        val pretestRecord = realm.parseScript(requiredScript)
        Assertions.assertTrue(pretestRecord.errors.isEmpty()) {
            val error = pretestRecord.errors[0]
            "(${error.lineNumber}, ${error.columnNumber}: ${error.message}"
        }
        val testRecord = realm.parseScript(script)
        Assertions.assertTrue(testRecord.errors.isEmpty()) {
            val error = testRecord.errors[0]
            "(${error.lineNumber}, ${error.columnNumber}: ${error.message}"
        }

        val pretestResult = agent.interpretedEvaluation(pretestRecord)
        Assertions.assertTrue(pretestResult.error == null) {
            pretestResult.error
        }
        val testResult = agent.interpretedEvaluation(testRecord)
        Assertions.assertTrue(testResult.error == null) {
            testResult.error
        }
    }

    companion object {
        private val test262Directory = File("./src/test/resources/test262/")
        private val testDirectory = File(test262Directory, "test")
        private val harnessDirectory = File(test262Directory, "harness")
        private val targetDirectory: File? = File(testDirectory, "built-ins/Object")
//        private val targetDirectory: File? = null
        private lateinit var pretestScript: String

        private val agent = Agent()

        @BeforeClass
        @JvmStatic
        fun setup() {
            if (!test262Directory.exists())
                throw IllegalStateException("The test262 repo must be cloned into src/test/resources/test262/")

            val assertText = File(harnessDirectory, "assert.js").readText()
            val stdText = File(harnessDirectory, "sta.js").readText()
            pretestScript = "$assertText\n$stdText\n"
        }

        @Parameterized.Parameters(name = "{index}: {0}")
        @JvmStatic
        fun test262testProvider(): List<Array<Any>> {
            return (targetDirectory ?: testDirectory).walkTopDown().onEnter {
                it.name != "intl402" && it.name != "annexB"
            }.filter {
                !it.isDirectory && !it.name.endsWith("_FIXTURE.js")
            }.toList().map {
                val name = it.absolutePath.replace(testDirectory.absolutePath, "")
                val contents = it.readText()

                val yamlStart = contents.indexOf("/*---")
                val yamlEnd = contents.indexOf("---*/")
                expect(yamlStart != -1 && yamlEnd != -1)
                val yaml = contents.substring(yamlStart + 5, yamlEnd)
                val metadata = Yaml.default.decodeFromString(Test262Metadata.serializer(), yaml)

                arrayOf(name, contents, metadata)
            }
        }
    }
}
