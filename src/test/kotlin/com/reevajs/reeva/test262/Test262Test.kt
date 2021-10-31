package com.reevajs.reeva.test262

import com.reevajs.reeva.Reeva
import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.RunResult
import com.reevajs.reeva.core.errors.DefaultErrorReporter
import com.reevajs.reeva.core.lifecycle.FileSourceType
import com.reevajs.reeva.core.lifecycle.LiteralSourceType
import com.reevajs.reeva.core.lifecycle.SourceInfo
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.toPrintableString
import com.reevajs.reeva.utils.expect
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.opentest4j.AssertionFailedError
import org.opentest4j.TestAbortedException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.charset.StandardCharsets

class Test262Test(
    private val name: String,
    private val file: File,
    private val script: String,
    private val metadata: Test262Metadata,
) {
    // TODO: Split these phases up
    private val shouldErrorDuringParse = metadata.negative?.phase.let {
        it == Negative.Phase.Parse || it == Negative.Phase.Early
    }

    private val shouldErrorDuringRuntime = metadata.negative?.phase.let {
        it == Negative.Phase.Resolution || it == Negative.Phase.Runtime
    }

    fun test() {
        val agent = Reeva.activeAgent

        try {
            Assumptions.assumeTrue(metadata.features?.any { "intl" in it.lowercase() } != true)
            Assumptions.assumeTrue(metadata.features?.any { it in excludedFeatures } != true)

            println("File: ${Test262Runner.testDirectoryStr}$name")

            val requiredScript = if (metadata.flags == null || Flag.Raw !in metadata.flags) {
                buildString {
                    append(Test262Runner.pretestScript)

                    metadata.includes?.forEach { include ->
                        append('\n')
                        append(File(Test262Runner.harnessDirectory, include).readText())
                    }

                    append("\n\n")
                }
            } else ""

            val realm = Reeva.makeRealm()
            val isModule = if (metadata.flags != null) Flag.Module in metadata.flags else false

            val pretestResult = agent.run(SourceInfo(
                realm,
                requiredScript,
                LiteralSourceType(isModule = false, "pretest"),
            ))

            Assertions.assertTrue(pretestResult is RunResult.Success) {
                "[PRETEST] ${getResultMessage(pretestResult)}"
            }

            if (metadata.flags != null && Flag.Async in metadata.flags) {
                runAsyncTest(agent, realm, isModule)
            } else {
                runSyncTest(agent, realm, isModule)
            }
        } catch (e: AssertionFailedError) {
            Test262Runner.testResults.add(
                Test262Runner.TestResult(
                    name, Test262Runner.TestResult.Status.Failed, e.message
                )
            )
            throw e
        } catch (e: TestAbortedException) {
            Test262Runner.testResults.add(
                Test262Runner.TestResult(
                    name, Test262Runner.TestResult.Status.Ignored, e.message
                )
            )
            throw e
        }

        Test262Runner.testResults.add(
            Test262Runner.TestResult(
                name, Test262Runner.TestResult.Status.Passed,
            )
        )
    }

    private fun runSyncTest(agent: Agent, realm: Realm, isModule: Boolean) {
        runTestCommon(agent, realm, isModule)
    }

    private fun runAsyncTest(agent: Agent, realm: Realm, isModule: Boolean) {
        val doneFunction = JSAsyncDoneFunction.create(realm)
        realm.globalObject.set("\$DONE", doneFunction)

        runTestCommon(agent, realm, isModule)

        Assertions.assertTrue(doneFunction.invocationCount == 1) {
            if (doneFunction.invocationCount == 0) {
                "\$DONE method not called"
            } else {
                "\$DONE method called ${doneFunction.invocationCount} times"
            }
        }

        Assertions.assertTrue(!Operations.toBoolean(doneFunction.result)) {
            "Expected \$DONE to be called with falsy value, received ${Operations.toString(realm, doneFunction.result)}"
        }
    }

    private fun runTestCommon(agent: Agent, realm: Realm, isModule: Boolean) {
        val theScript = if (metadata.flags?.contains(Flag.OnlyStrict) == true && !isModule) {
            "'use strict'; $script"
        } else script

        if ("buildString(" in theScript) {
            Assertions.assertTrue(false) {
                "For some reason this function infinitely loops"
            }
        }

        val testResult = agent.run(
            SourceInfo(
                realm,
                theScript,
                FileSourceType(file, isModule),
            )
        )

        if (isModule && testResult !is RunResult.Success)
            println()

        if (shouldErrorDuringParse) {
            Assertions.assertTrue(testResult is RunResult.ParseError) {
                "Expected parse error during execution\n${getResultMessage(testResult)}"
            }
        } else if (shouldErrorDuringRuntime) {
            Assertions.assertTrue(testResult is RunResult.RuntimeError) {
                "Expected ${metadata.negative!!.type} during execution\n${getResultMessage(testResult)}"
            }
        } else {
            Assertions.assertTrue(testResult is RunResult.Success) {
                "Expected no error during execution\n${getResultMessage(testResult)}"
            }
        }
    }

    private fun getResultMessage(runResult: RunResult): String {
        if (runResult is RunResult.Success)
            return runResult.result.toPrintableString()

        return ByteArrayOutputStream().use { baos ->
            PrintStream(baos, true, StandardCharsets.UTF_8.name()).use { ps ->
                runResult.unwrap(DefaultErrorReporter(ps))
            }
            baos.toString(StandardCharsets.UTF_8.name())
        }
    }

    companion object {
        private val excludedFeatures = listOf(
            "async-iteration",
            "class-static-methods-private",
            "class-methods-private",
            "class-static-fields-private",
            "class-fields-private",
        )
    }
}
