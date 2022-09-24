package com.reevajs.reeva.test262

import com.charleskorn.kaml.Yaml
import com.reevajs.reeva.Reeva
import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.HostHooks
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.utils.expect
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentLinkedQueue

class Test262Runner {
    @TestFactory
    fun test262testProvider(): List<DynamicNode> {
        return getDynamicTests(target ?: testDirectory)
    }

    private fun getDynamicTests(folder: File): List<DynamicNode> {
        require(folder.isDirectory)

        return folder.listFiles()!!.filter {
            when {
                it.name == "intl402" || it.name == "annexB" -> false
                it.name.endsWith("_FIXTURE.js") || it.name.endsWith("_FIXTURE.json") -> false
                "S13.2.1_A1_T1.js" in it.name -> false // the nested function call test
                "S7.8.5_A2.1_T2.js" in it.name -> false // a regexp test that hangs
                else -> true
            }
        }.map {
            if (it.isDirectory) {
                DynamicContainer.dynamicContainer(it.name, getDynamicTests(it))
            } else {
                val name = it.absolutePath.replace(testDirectory.absolutePath, "")
                val contents = it.readText()

                val yamlStart = contents.indexOf("/*---")
                val yamlEnd = contents.indexOf("---*/")
                expect(yamlStart != -1 && yamlEnd != -1)
                val yaml = contents.substring(yamlStart + 5, yamlEnd)
                val metadata = Yaml.default.decodeFromString(Test262Metadata.serializer(), yaml)

                DynamicTest.dynamicTest(it.name) {
                    Test262Test(name, it, contents, metadata).test()
                }
            }
        }
    }

    @Serializable
    data class TestResult(
        val name: String,
        val status: Status,
        val extra: String? = null
    ) {
        enum class Status {
            Passed,
            Failed,
            Ignored
        }
    }

    companion object {
        val test262Directory = File("./test262")
        val testDirectory = File(test262Directory, "test")
        val testDirectoryStr = testDirectory.absolutePath
        val harnessDirectory = File(test262Directory, "harness")
        // val target: File? = File(testDirectory, "built-ins/ArrayBuffer/prototype/byteLength/detached-buffer.js")
        val target: File? = null
        lateinit var pretestScript: String

        val testResults = ConcurrentLinkedQueue<TestResult>()
        private val json = Json { prettyPrint = true }

        @BeforeAll
        @JvmStatic
        fun setup() {
            if (!test262Directory.exists())
                throw IllegalStateException("The test262 repo must be cloned into ./test262/")

            val assertText = File(harnessDirectory, "assert.js").readText()
            val staText = File(harnessDirectory, "sta.js").readText()
            pretestScript = "$assertText\n$staText\n"

            Reeva.setup()
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            val results = testResults.sortedBy { it.name }
            val filePath = "./demo/test_results/${LocalDateTime.now()}.json"

            File(filePath).writeText(
                json.encodeToString(results)
            )
        }
    }
}
