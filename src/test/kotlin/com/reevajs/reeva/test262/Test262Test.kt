package com.reevajs.reeva.test262

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.core.errors.ThrowException
import com.reevajs.reeva.core.lifecycle.FileSourceInfo
import com.reevajs.reeva.core.lifecycle.LiteralSourceInfo
import com.reevajs.reeva.core.lifecycle.SourceInfo
import com.reevajs.reeva.parsing.ParsingError
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.toPrintableString
import com.reevajs.reeva.transformer.opcodes.Throw
import com.reevajs.reeva.utils.Result
import com.reevajs.reeva.utils.unreachable
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.opentest4j.AssertionFailedError
import org.opentest4j.TestAbortedException
import strikt.api.expectThat
import strikt.assertions.isA
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
        val agent = Agent.activeAgent

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

            val realm = agent.makeRealm()
            val isModule = if (metadata.flags != null) Flag.Module in metadata.flags else false

            val pretestSourceInfo = LiteralSourceInfo("pretest", requiredScript, isModule = false)
            try {
                val pretestResult = execute(agent, realm, pretestSourceInfo)
                if (pretestResult.hasError) {
                    agent.errorReporter.reportParseError(pretestSourceInfo, pretestResult.error())
                    Assertions.fail<Nothing>()
                }
            } catch (e: Throwable) {
                Assertions.fail(e)
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

    fun execute(agent: Agent, realm: Realm, sourceInfo: SourceInfo): Result<ParsingError, JSValue> {
        return agent.compile(realm, sourceInfo).mapValue { it.execute() }
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

        val sourceInfo = FileSourceInfo(file, isModule, sourceText = theScript)

        try {
            val testResult = execute(agent, realm, sourceInfo)

            if (testResult.hasError) {
                Assertions.assertTrue(shouldErrorDuringParse) {
                    agent.errorReporter.reportParseError(sourceInfo, testResult.error())
                    "Expected no parse error during execution"
                }
            } else {
                Assertions.assertTrue(!shouldErrorDuringParse) {
                    "Expected parse error during execution"
                }
            }

            Assertions.assertTrue(!shouldErrorDuringRuntime) {
                "Expected ${metadata.negative!!.type} during execution, but found no error"
            }


        } catch (e: ThrowException) {
            Assertions.assertTrue(shouldErrorDuringRuntime) {
                agent.errorReporter.reportRuntimeError(sourceInfo, e)
                "Expected no runtime error during execution"
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
