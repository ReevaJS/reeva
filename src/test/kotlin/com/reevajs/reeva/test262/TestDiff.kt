package com.reevajs.reeva.test262

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

const val OLD_NAME = "old.json"
const val NEW_NAME = "new.json"

fun main() {
    val oldJson = File("demo/test_results", OLD_NAME).readText()
    val newJson = File("demo/test_results", NEW_NAME).readText()

    val json = Json { }
    val oldContents = json.decodeFromString<List<Test262Runner.TestResult>>(oldJson)
    val newContents = json.decodeFromString<List<Test262Runner.TestResult>>(newJson)

    val newMap = newContents.associateBy { it.name }

    for (test in oldContents) {
        val newTest = newMap[test.name]!!

        if (test.status == Test262Runner.TestResult.Status.Passed &&
            newTest.status != Test262Runner.TestResult.Status.Passed
        ) {
            val file = File(Test262Runner.testDirectory, test.name)
            println(file.absolutePath)
        }
    }
}
