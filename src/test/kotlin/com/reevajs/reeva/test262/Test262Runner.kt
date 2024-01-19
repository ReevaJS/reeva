package com.reevajs.reeva.test262

import com.charleskorn.kaml.Yaml
import com.reevajs.reeva.utils.expect
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.*
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue

class Test262Runner {
    @TestFactory
    fun test262testProvider(): List<DynamicNode> {
        if (target?.isFile == true)
            return listOf(makeTest(target))

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
            } else makeTest(it)
        }
    }

    private fun makeTest(file: File): DynamicTest {
        require(file.isFile)

        val name = file.absolutePath.replace(testDirectory.absolutePath, "")
        val contents = file.readText()

        val yamlStart = contents.indexOf("/*---")
        val yamlEnd = contents.indexOf("---*/")
        expect(yamlStart != -1 && yamlEnd != -1)
        val yaml = contents.substring(yamlStart + 5, yamlEnd)
        val metadata = Yaml.default.decodeFromString(Test262Metadata.serializer(), yaml)

        return DynamicTest.dynamicTest(file.name) {
            Test262Test(name, file, contents, metadata).test()
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
        // val target: File? = File(testDirectory, "built-ins/Boolean/S15.6.1.1_A1_T1.js")
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
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            val results = testResults.sortedBy { it.name }
            val dateTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val filePath = "./demo/test_results/$dateTime.json"

            File(filePath).writeText(
                json.encodeToString(results)
            )
        }
    }
}
