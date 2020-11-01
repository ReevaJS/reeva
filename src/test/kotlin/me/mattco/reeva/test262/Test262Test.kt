package me.mattco.reeva.test262

import me.mattco.reeva.Reeva
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.primitives.JSFalse
import org.junit.jupiter.api.*
import java.io.File

class Test262Test(private val script: String, private val metadata: Test262Metadata) {
    fun test() {
        Assumptions.assumeTrue(metadata.negative == null)
        Assumptions.assumeTrue(metadata.features?.any { "intl" in it.toLowerCase() } != true)

        var requiredScript = Test262Runner.pretestScript

        metadata.includes?.forEach { include ->
            requiredScript += '\n'
            requiredScript += File(Test262Runner.harnessDirectory, include).readText()
        }

        val realm = Reeva.makeRealm()
        realm.initObjects()
        realm.setGlobalObject(Test262GlobalObject.create(realm))

        val pretestResult = Reeva.evaluate("$requiredScript\n\n", realm)
        Assertions.assertTrue(!pretestResult.isError) {
            Reeva.with(realm) {
                Operations.toString(pretestResult.value).string
            }
        }

        if (metadata.flags != null && Flag.Async in metadata.flags) {
            runAsyncTest(realm)
        } else {
            runSyncTest(realm)
        }
    }

    private fun runSyncTest(realm: Realm) {
        val testResult = Reeva.evaluate(script, realm)
        Assertions.assertTrue(!testResult.isError) {
            Reeva.with(realm) {
                Operations.toString(testResult.value).string
            }
        }
    }

    private fun runAsyncTest(realm: Realm) {
        val doneFunction = JSAsyncDoneFunction.create(realm)
        realm.globalObject.set("\$DONE", doneFunction)

        val testResult = Reeva.evaluate(script, realm)
        Assertions.assertTrue(!testResult.isError) {
            Reeva.with(realm) {
                Operations.toString(testResult.value).string
            }
        }

        Assertions.assertTrue(doneFunction.invocationCount == 1) {
            if (doneFunction.invocationCount == 0) {
                "\$DONE method not called"
            } else {
                "\$DONE method called ${doneFunction.invocationCount} times"
            }
        }

        Reeva.with(realm) {
            Assertions.assertTrue(Operations.toBoolean(doneFunction.result) == JSFalse) {
                "Expected \$DONE to be called with falsy value, received ${Operations.toString(doneFunction.result)}"
            }
        }
    }
}
