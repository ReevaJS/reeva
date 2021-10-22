package com.reevajs.reeva.test262

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

const val OLD_NAME = "old.json"
const val NEW_NAME = "new.json"

fun main() {
    val oldJson = File("demo/test_results", OLD_NAME).readText()
    val newJson = File("demo/test_results", NEW_NAME).readText()

    val oldContents = Json.decodeFromString<List<Test262Runner.TestResult>>(oldJson)
    val newContents = Json.decodeFromString<List<Test262Runner.TestResult>>(newJson)

    val newMap = newContents.associateBy { it.name }

    val newPass = mutableListOf<String>()
    val newFail = mutableListOf<String>()

    for (test in oldContents) {
        val newTest = newMap[test.name]!!
        val wasSuccess = test.status == Test262Runner.TestResult.Status.Passed
        val isSuccess = newTest.status == Test262Runner.TestResult.Status.Passed

        if (wasSuccess != isSuccess) {
            val file = File(Test262Runner.testDirectory, test.name)
            (if (isSuccess) newPass else newFail).add(file.absolutePath)
        }
    }

    println("New passes: ${newPass.size}, new fails: ${newFail.size}")

    for (path in newPass)
        println("+ $path")

    for (path in newFail)
        println("- $path")
}
