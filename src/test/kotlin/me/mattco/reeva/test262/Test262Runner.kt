package me.mattco.reeva.test262

import com.charleskorn.kaml.Yaml
import me.mattco.reeva.Reeva
import me.mattco.reeva.utils.expect
import org.junit.jupiter.api.*
import java.io.File

class Test262Runner {
    @TestFactory
    fun test262testProvider(): List<DynamicTest> {
        return (targetDirectory ?: testDirectory).walkTopDown().onEnter {
            it.name != "intl402" && it.name != "annexB"
        }.filter {
            !it.isDirectory && !it.name.endsWith("_FIXTURE.js") && "S13.2.1_A1_T1.js" !in it.name // the nested function call test
        }.toList().map { file ->
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

    companion object {
        val test262Directory = File("./src/test/resources/test262/")
        val testDirectory = File(test262Directory, "test")
        val testDirectoryStr = testDirectory.absolutePath
        val harnessDirectory = File(test262Directory, "harness")
        val targetDirectory: File? = File(testDirectory, "language/module-code")
//        val targetDirectory: File? = null
        lateinit var pretestScript: String

        @BeforeAll
        @JvmStatic
        fun setup() {
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
            Reeva.teardown()
        }
    }
}
