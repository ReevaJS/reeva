package me.mattco.reeva.core.modules.records

import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.interpreter.Interpreter
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.module.JSModuleNamespaceObject
import me.mattco.reeva.runtime.primitives.JSEmpty
import me.mattco.reeva.utils.ecmaAssert
import kotlin.math.min

abstract class CyclicModuleRecord(
    realm: Realm,
    environment: EnvRecord?,
    namespace: JSModuleNamespaceObject?,
    val requestedModules: List<String>,
) : ModuleRecord(realm, environment, namespace) {
    var status = Status.Unlinked
    var evaluationError: Throwable? = null
    var dfsIndex = -1
    var dfsAncestorIndex = -1

    protected abstract fun initializeEnvironment()

    override fun link() {
        ecmaAssert(status != Status.Linking && status != Status.Evaluating)
        val stack = mutableListOf<ModuleRecord>()
        try {
            innerModuleLinking(realm, this, stack, 0)
            ecmaAssert(status == Status.Linked || status == Status.Evaluated)
            ecmaAssert(stack.isEmpty())
        } catch (e: ThrowException) {
            stack.filterIsInstance<CyclicModuleRecord>().forEach { module ->
                ecmaAssert(module.status == Status.Linking)
                module.status = Status.Unlinked
                module.environment = null
                module.dfsIndex = -1
                module.dfsAncestorIndex = -1
            }
            ecmaAssert(status == Status.Unlinked)
            throw e
        }
    }

    abstract fun executeModule(interpreter: Interpreter): JSValue

    override fun evaluate(interpreter: Interpreter): JSValue {
        ecmaAssert(status == Status.Linked || status == Status.Evaluated)
        val stack = mutableListOf<ModuleRecord>()
        return try {
            val (_, result) = innerModuleEvaluation(interpreter, realm, this, stack, 0)
            ecmaAssert(status == Status.Evaluated && evaluationError == null)
            ecmaAssert(stack.isEmpty())
            result
        } catch (e: ThrowException) {
            stack.filterIsInstance<CyclicModuleRecord>().forEach { module ->
                ecmaAssert(module.status == Status.Evaluating)
                module.status = Status.Evaluated
                module.evaluationError = e
            }
            ecmaAssert(status == Status.Evaluated && evaluationError == e)
            throw e
        }
    }

    enum class Status {
        Unlinked,
        Linking,
        Linked,
        Evaluating,
        Evaluated,
    }

    companion object {
        private fun innerModuleLinking(realm: Realm, module: ModuleRecord, stack: MutableList<ModuleRecord>, _index: Int): Int {
            if (module !is CyclicModuleRecord) {
                module.link()
                return _index
            }

            var index = _index
            if (module.status in listOf(Status.Linking, Status.Linked, Status.Evaluated))
                return index

            ecmaAssert(module.status == Status.Unlinked)
            module.status = Status.Linking
            module.dfsIndex = index
            module.dfsAncestorIndex = index
            index++
            stack.add(module)
            module.requestedModules.forEach { required ->
                val requiredModule = realm.moduleResolver!!.hostResolveImportedModule(module, required)
                index = innerModuleLinking(realm, requiredModule, stack, index)
                if (requiredModule is CyclicModuleRecord) {
                    ecmaAssert(requiredModule.status.let { it == Status.Linking || it == Status.Linked || it == Status.Evaluated })
                    if (requiredModule.status == Status.Linking) {
                        ecmaAssert(requiredModule in stack)
                        module.dfsAncestorIndex = min(module.dfsAncestorIndex, requiredModule.dfsAncestorIndex)
                    }
                }
            }

            module.initializeEnvironment()
            ecmaAssert(stack.count { it == module } == 1)
            ecmaAssert(module.dfsAncestorIndex <= module.dfsIndex)
            if (module.dfsAncestorIndex == module.dfsIndex) {
                var done = false
                while (!done) {
                    val requiredModule = stack.removeLast()
                    ecmaAssert(requiredModule is CyclicModuleRecord)
                    requiredModule.status = Status.Linked
                    if (requiredModule == module)
                        done = true
                }
            }

            return index
        }

        private fun innerModuleEvaluation(interpreter: Interpreter, realm: Realm, module: ModuleRecord, stack: MutableList<ModuleRecord>, _index: Int): Pair<Int, JSValue> {
            if (module !is CyclicModuleRecord) {
                module.evaluate(interpreter)
                return _index to JSEmpty
            }

            var index = _index
            if (module.status == Status.Evaluated) {
                if (module.evaluationError == null)
                    return index to JSEmpty
                throw module.evaluationError!!
            }

            if (module.status == Status.Evaluating)
                return index to JSEmpty

            ecmaAssert(module.status == Status.Linked)
            module.status = Status.Evaluating
            module.dfsIndex = index
            module.dfsAncestorIndex = index
            index++
            stack.add(module)
            module.requestedModules.forEach { required ->
                val requiredModule = realm.moduleResolver!!.hostResolveImportedModule(module, required)
                index = innerModuleEvaluation(interpreter, realm, requiredModule, stack, index).first
                if (requiredModule is CyclicModuleRecord) {
                    ecmaAssert(requiredModule.status.let { it == Status.Evaluating || it == Status.Evaluated })
                    if (requiredModule.status == Status.Evaluating) {
                        ecmaAssert(requiredModule in stack)
                        module.dfsAncestorIndex = min(module.dfsAncestorIndex, requiredModule.dfsAncestorIndex)
                    }
                }
            }

            val value = module.executeModule(interpreter)
            ecmaAssert(stack.count { it == module } == 1)
            ecmaAssert(module.dfsAncestorIndex <= module.dfsIndex)
            if (module.dfsAncestorIndex == module.dfsIndex) {
                var done = false
                while (!done) {
                    val requiredModule = stack.removeLast()
                    ecmaAssert(requiredModule is CyclicModuleRecord)
                    requiredModule.status = Status.Evaluated
                    if (requiredModule == module)
                        done = true
                }
            }

            return index to value
        }
    }
}
