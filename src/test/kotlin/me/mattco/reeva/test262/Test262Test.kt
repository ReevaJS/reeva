package me.mattco.reeva.test262

import me.mattco.reeva.Reeva
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.modules.resolver.DefaultModuleResolver
import me.mattco.reeva.runtime.Operations
import org.junit.jupiter.api.*
import org.opentest4j.AssertionFailedError
import org.opentest4j.TestAbortedException
import java.io.File

class Test262Test(
    private val name: String,
    private val file: File,
    private val script: String,
    private val metadata: Test262Metadata
) {
    // TODO: Split these phases up
    private val shouldErrorDuringParse = metadata.negative?.phase.let { it == Negative.Phase.Parse || it == Negative.Phase.Early }
    private val shouldErrorDuringRuntime = metadata.negative?.phase.let { it == Negative.Phase.Resolution || it == Negative.Phase.Runtime }

    fun test() {
        try {
            Assumptions.assumeTrue(metadata.features?.any { "intl" in it.toLowerCase() } != true)
            Assumptions.assumeTrue(metadata.features?.any { it in excludedFeatures } != true)

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

            val pretestResult = Reeva.evaluateScript("$requiredScript\n\n", realm)

            Assertions.assertTrue(!pretestResult.isError) {
                Reeva.with(realm) {
                    Operations.toString(pretestResult.value).string
                }
            }

            if (metadata.flags != null && Flag.Async in metadata.flags) {
                runAsyncTest(realm, isModule)
            } else {
                runSyncTest(realm, isModule)
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

    private fun runSyncTest(realm: Realm, isModule: Boolean) {
        runTestCommon(realm, isModule)
    }

    private fun runAsyncTest(realm: Realm, isModule: Boolean) {
        val doneFunction = JSAsyncDoneFunction.create(realm)
        realm.globalObject.set("\$DONE", doneFunction)

        runTestCommon(realm, isModule)

        Assertions.assertTrue(doneFunction.invocationCount == 1) {
            if (doneFunction.invocationCount == 0) {
                "\$DONE method not called"
            } else {
                "\$DONE method called ${doneFunction.invocationCount} times"
            }
        }

        Reeva.with(realm) {
            Assertions.assertTrue(!Operations.toBoolean(doneFunction.result)) {
                "Expected \$DONE to be called with falsy value, received ${Operations.toString(doneFunction.result)}"
            }
        }
    }

    private fun runTestCommon(realm: Realm, isModule: Boolean) {
        val theScript = if (metadata.flags?.contains(Flag.OnlyStrict) == true) {
            "'use strict'; $script"
        } else script

        val task = Test262EvaluationTask(theScript, realm, isModule)
        val testResult = Reeva.getAgent().runTask(task)

        if (shouldErrorDuringParse || shouldErrorDuringRuntime) {
            val expectedPhase = if (shouldErrorDuringParse) Negative.Phase.Parse else Negative.Phase.Runtime
            val phaseName = expectedPhase.name.toLowerCase()

            Assertions.assertTrue(testResult.isError && task.phaseFailed == expectedPhase) {
                "Expected failure during $phaseName, but parsing succeeded"
            }

            val expectedClass = "JS${metadata.negative!!.type}Object"
            val actualClass = testResult.value::class.simpleName

            Assertions.assertTrue(actualClass == expectedClass) {
                "Expected $expectedClass to be thrown at $phaseName time, but found $actualClass"
            }
        } else {
            Assertions.assertTrue(!testResult.isError) {
                Reeva.with(realm) {
                    Operations.toString(testResult.value).string
                }
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
