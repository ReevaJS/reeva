package me.mattco.reeva.test262

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.mattco.reeva.Reeva
import me.mattco.reeva.utils.expect
import org.junit.jupiter.api.*
import java.io.File
import java.time.LocalDateTime
import java.time.ZonedDateTime

class Test262Runner {
    @TestFactory
    fun test262testProvider(): List<DynamicTest> {
        return (targetDirectory ?: testDirectory).walkTopDown().onEnter {
            it.name != "intl402" && it.name != "annexB"
        }.filter {
            !it.isDirectory && !it.name.endsWith("_FIXTURE.js") &&
                "S13.2.1_A1_T1.js" !in it.name && // the nested function call test
                "S7.8.5_A2.1_T2.js" !in it.name // a regexp test that hangs
        }.toList().sortedBy { it.absolutePath }.map { file ->
            val name = file.absolutePath.replace(testDirectory.absolutePath, "")
            val contents = file.readText()

            val yamlStart = contents.indexOf("/*---")
            val yamlEnd = contents.indexOf("---*/")
            expect(yamlStart != -1 && yamlEnd != -1)
            val yaml = contents.substring(yamlStart + 5, yamlEnd)
            val metadata = Yaml.default.decodeFromString(Test262Metadata.serializer(), yaml)

            DynamicTest.dynamicTest(name) {
                Test262Test(name, file, contents, metadata).test()
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
        val test262Directory = File("./src/test/resources/test262/")
        val testDirectory = File(test262Directory, "test")
        val testDirectoryStr = testDirectory.absolutePath
        val harnessDirectory = File(test262Directory, "harness")
        val targetDirectory: File? = File(testDirectory, "language/statements/break")
//        val targetDirectory: File? = null
        lateinit var pretestScript: String

        val testResults = mutableListOf<TestResult>()

        @BeforeAll
        @JvmStatic
        fun setup() {
            Reeva.EMIT_CLASS_FILES = false

            if (!test262Directory.exists())
                throw IllegalStateException("The test262 repo must be cloned into src/test/resources/test262/")

            val assertText = File(harnessDirectory, "assert.js").readText()
            val staText = File(harnessDirectory, "sta.js").readText()
            pretestScript = "$assertText\n$staText\n"

            Reeva.setup()
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            File("./demo/test_results/${LocalDateTime.now()}.json").writeText(
                Json { prettyPrint = true }.encodeToString(testResults.sortedBy { it.name })
            )

            Reeva.teardown()
        }
    }
}
