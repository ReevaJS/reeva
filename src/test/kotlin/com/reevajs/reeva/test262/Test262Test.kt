package com.reevajs.reeva.test262

import com.reevajs.reeva.Reeva
import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.lifecycle.ExecutionResult
import com.reevajs.reeva.runtime.Operations
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.opentest4j.AssertionFailedError
import org.opentest4j.TestAbortedException
import java.io.File

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

            if (metadata.flags != null) {
                Assumptions.assumeTrue(Flag.Module !in metadata.flags)
                Assumptions.assumeTrue(Flag.Async !in metadata.flags)
            }
            val isModule = false

            val pretestResult = agent.run(requiredScript, realm)

            Assertions.assertTrue(!pretestResult.isError) {
                "Expected pretest to run without exception, but received $pretestResult"
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
        if (isModule)
            TODO()

        val theScript = if (metadata.flags?.contains(Flag.OnlyStrict) == true) {
            "'use strict'; $script"
        } else script

        if ("buildString(" in theScript)
            TODO("For some reason this function infinitely loops")

        val testResult = agent.run(theScript, realm)

        if (shouldErrorDuringParse || shouldErrorDuringRuntime) {
            Assertions.assertTrue(testResult.isError) {
                "Expected error during execution, but no error was generated"
            }

            // FIXME: Implement
            if (shouldErrorDuringParse) {
                Assertions.assertTrue(testResult is ExecutionResult.ParseError) {
                    "Expected ParseError, but received ${testResult::class.simpleName} ($testResult)"
                }
            } else {
                Assertions.assertTrue(testResult is ExecutionResult.RuntimeError) {
                    "Expected RuntimeError, but received ${testResult::class.simpleName} ($testResult)"
                }

                val expectedClass = "JS${metadata.negative!!.type}Object"
                val actualClass = (testResult as ExecutionResult.RuntimeError).value::class.simpleName

                Assertions.assertTrue(actualClass == expectedClass) {
                    "Expected $expectedClass to be thrown, but found $actualClass"
                }
            }
        } else {
            Assertions.assertTrue(testResult is ExecutionResult.Success) {
                "Expected execution to complete normally, but received error " +
                    "${testResult::class.simpleName} ($testResult)"
            }
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
