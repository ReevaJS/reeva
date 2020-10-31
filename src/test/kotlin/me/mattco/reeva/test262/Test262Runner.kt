package me.mattco.reeva.test262

import com.charleskorn.kaml.Yaml
import me.mattco.reeva.Reeva
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.primitives.JSFalse
import me.mattco.reeva.utils.expect
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class Test262Runner(private var name: String?, private var script: String?, private var metadata: Test262Metadata?) {
    @Test
    fun test262Test() {
        Assumptions.assumeTrue(metadata!!.negative == null)
        Assumptions.assumeTrue(metadata!!.features?.any { "intl" in it.toLowerCase() } != true)

        var requiredScript = pretestScript

        metadata!!.includes?.forEach { include ->
            requiredScript += '\n'
            requiredScript += File(harnessDirectory, include).readText()
        }

        val realm = Reeva.makeRealm()

        val pretestResult = Reeva.evaluate("$requiredScript\n\n", realm)
        Assertions.assertTrue(!pretestResult.isError) {
            Reeva.with(realm) {
                Operations.toString(pretestResult.value).string
            }
        }

        if (metadata!!.flags != null && Flag.Async in metadata!!.flags!!) {
            runAsyncTest(realm)
        } else {
            runSyncTest(realm)
        }

        name = null
        script = null
        metadata = null
    }

    private fun runSyncTest(realm: Realm) {
        val testResult = Reeva.evaluate(script!!, realm)
        Assertions.assertTrue(!testResult.isError) {
            Reeva.with(realm) {
                Operations.toString(testResult.value).string
            }
        }
    }

    private fun runAsyncTest(realm: Realm) {
        val doneFunction = JSAsyncDoneFunction.create(realm)
        realm.globalObject.set("\$DONE", doneFunction)

        val testResult = Reeva.evaluate(script!!, realm)
        Assertions.assertTrue(!testResult.isError) {
            Reeva.with(realm) {
                Operations.toString(testResult.value).string
            }
        }

        Assertions.assertTrue(doneFunction.invocationCount == 1) {
            if (doneFunction.invocationCount == 0) {
                "\$DONE method not called"
            } else {
                "\$DONE method called ${doneFunction.invocationCount} times"
            }
        }

        Reeva.with(realm) {
            Assertions.assertTrue(Operations.toBoolean(doneFunction.result) == JSFalse) {
                "Expected \$DONE to be called with falsy value, received ${Operations.toString(doneFunction.result)}"
            }
        }
    }

    companion object {
        private val test262Directory = File("./src/test/resources/test262/")
        private val testDirectory = File(test262Directory, "test")
        private val harnessDirectory = File(test262Directory, "harness")
        private val targetDirectory: File? = File(testDirectory, "built-ins/Promise")
//        private val targetDirectory: File? = null
        private lateinit var pretestScript: String

        @BeforeClass
        @JvmStatic
        fun setup() {
            if (!test262Directory.exists())
                throw IllegalStateException("The test262 repo must be cloned into src/test/resources/test262/")

            val assertText = File(harnessDirectory, "assert.js").readText()
            val staText = File(harnessDirectory, "sta.js").readText()
            pretestScript = "$assertText\n$staText\n"

            Reeva.setup()
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

        @AfterClass
        @JvmStatic
        fun teardown() {
            Reeva.teardown()
        }
    }
}
