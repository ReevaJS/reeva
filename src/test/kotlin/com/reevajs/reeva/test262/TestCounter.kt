package com.reevajs.reeva.test262

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import com.reevajs.reeva.utils.expect
import java.io.File

const val FILE_NAME = "new.json"

fun main() {
    val json = File("demo/test_results", FILE_NAME).readText()
    val contents = Json.Default.decodeFromString<List<Test262Runner.TestResult>>(json)

    val directories = mutableMapOf<File, Int>()

    for (test in contents) {
        val directory = File(Test262Runner.testDirectory, test.name).parentFile
        expect(directory.isDirectory)

        if (directory in directories) {
            if (test.status == Test262Runner.TestResult.Status.Failed)
                directories[directory] = directories[directory]!! + 1
        } else {
            directories[directory] = 0
        }
    }

    directories.toList().sortedBy { it.second }.forEach {
        val path = it.first.absolutePath
        if (!path.lowercase().let { "dstr" in it || "regex" in it || "eval" in it })
            println("${it.first.absolutePath}: ${it.second}")
    }
}