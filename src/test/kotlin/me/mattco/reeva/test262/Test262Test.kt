package me.mattco.reeva.test262

import me.mattco.reeva.Reeva
import me.mattco.reeva.core.Agent
import me.mattco.reeva.core.EvaluationResult
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.modules.resolver.DefaultModuleResolver
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.toJSString
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
    private val shouldErrorDuringParse = metadata.negative?.phase.let { it == Negative.Phase.Parse || it == Negative.Phase.Early }
    private val shouldErrorDuringRuntime = metadata.negative?.phase.let { it == Negative.Phase.Resolution || it == Negative.Phase.Runtime }

    fun test() {
        val agent = Reeva.activeAgent

        try {
            Assumptions.assumeTrue(metadata.features?.any { "intl" in it.toLowerCase() } != true)
            Assumptions.assumeTrue(metadata.features?.any { it in excludedFeatures } != true)

            // TODO: This function is used by many regexp tests, and often leads to for loops
            // which are too intensive to run in Reeva. We'll have to do some more optimizations
            // before we can allow these tests.
            Assumptions.assumeTrue("buildString({" !in script)

            println("File: ${Test262Runner.testDirectoryStr}$name")

            var requiredScript = Test262Runner.pretestScript

            metadata.includes?.forEach { include ->
                requiredScript += '\n'
                requiredScript += File(Test262Runner.harnessDirectory, include).readText()
            }

            val realm = Reeva.makeRealm()
            realm.initObjects()
            realm.setGlobalObject(Test262GlobalObject.create(realm))

            val isModule = metadata.flags != null && Flag.Module in metadata.flags

            if (isModule)
                realm.moduleResolver = DefaultModuleResolver(realm, file.parentFile)

            val pretestResult = agent.run("$requiredScript\n\n", realm)

            Assertions.assertTrue(!pretestResult.isError) {
                agent.withRealm(realm) {
                    Operations.toString(pretestResult.value).string
                }
            }

            if (metadata.flags != null && Flag.Async in metadata.flags) {
                runAsyncTest(agent, realm, isModule)
            } else {
                runSyncTest(agent, realm, isModule)
            }
        } catch (e: AssertionFailedError) {
            Test262Runner.testResults.add(Test262Runner.TestResult(
                name, Test262Runner.TestResult.Status.Failed, e.message
            ))
            throw e
        } catch (e: TestAbortedException) {
            Test262Runner.testResults.add(Test262Runner.TestResult(
                name, Test262Runner.TestResult.Status.Ignored, e.message
            ))
            throw e
        }

        Test262Runner.testResults.add(Test262Runner.TestResult(
            name, Test262Runner.TestResult.Status.Passed,
        ))
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

        agent.withRealm(realm) {
            Assertions.assertTrue(!Operations.toBoolean(doneFunction.result)) {
                "Expected \$DONE to be called with falsy value, received ${Operations.toString(doneFunction.result)}"
            }
        }
    }

    private fun runTestCommon(agent: Agent, realm: Realm, isModule: Boolean) {
        if (isModule)
            TODO()

        val theScript = if (metadata.flags?.contains(Flag.OnlyStrict) == true) {
            "'use strict'; $script"
        } else script

        val testResult = agent.run(theScript, realm)

        if (shouldErrorDuringParse || shouldErrorDuringRuntime) {
            if (shouldErrorDuringParse) {
                Assertions.assertTrue(testResult is EvaluationResult.ParseFailure) {
                    val str = agent.withRealm(realm) {
                        testResult.value.toJSString()
                    }
                    "Expected EvaluationResult.ParseFailure, but received EvaluationResult.${testResult::class.simpleName}{$str}"
                }
            } else {
                Assertions.assertTrue(testResult is EvaluationResult.RuntimeError) {
                    val str = agent.withRealm(realm) {
                        testResult.value.toJSString()
                    }
                    "Expected EvaluationResult.RuntimeError, but received EvaluationResult.${testResult::class.simpleName}{$str}"
                }
            }

            val expectedClass = "JS${metadata.negative!!.type}Object"
            val actualClass = testResult.value::class.simpleName

            Assertions.assertTrue(actualClass == expectedClass) {
                "Expected $expectedClass to be thrown, but found $actualClass"
            }
        } else {
            Assertions.assertTrue(testResult is EvaluationResult.Success) {
                val str = agent.withRealm(realm) {
                    testResult.value.toJSString()
                }
                "Expected EvaluationResult.Success, but received EvaluationResult.${testResult::class.simpleName}{$str}"
            }
        }
    }

    companion object {
        private val excludedFeatures = listOf(
            "generators",
            "async-functions",
            "async-iteration",
            "class-static-methods-private",
            "class-methods-private",
            "class-static-fields-private",
            "class-fields-private",
        )
    }
}
