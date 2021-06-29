package me.mattco.reeva.core

import me.mattco.reeva.Reeva
import me.mattco.reeva.ast.ScriptNode
import me.mattco.reeva.core.environment.GlobalEnvRecord
import me.mattco.reeva.interpreter.ExecutionResult
import me.mattco.reeva.interpreter.Interpreter
import me.mattco.reeva.interpreter.transformer.Transformer
import me.mattco.reeva.interpreter.transformer.opcodes.IrPrinter
import me.mattco.reeva.parser.Parser
import me.mattco.reeva.parser.ParsingResult
import java.nio.ByteOrder

class Agent {
    // Used to ensure names of various things are unique
    @Volatile
    private var uniqueId = 0

    var printAST = false
    var printIR = false

    var hostHooks = HostHooks()

    val byteOrder: ByteOrder = ByteOrder.nativeOrder()
    val isLittleEndian: Boolean
        get() = byteOrder == ByteOrder.LITTLE_ENDIAN
    val isBigEndian: Boolean
        get() = byteOrder == ByteOrder.BIG_ENDIAN

    private val pendingMicrotasks = ArrayDeque<() -> Unit>()

    init {
        Reeva.allAgents.add(this)
    }

    fun run(script: String, realm: Realm): ExecutionResult {
        val ast = when (val astResult = Parser(script).parseScript()) {
            is ParsingResult.InternalError -> return ExecutionResult.InternalError(astResult.cause)
            is ParsingResult.ParseError -> return ExecutionResult.ParseError(
                astResult.reason,
                astResult.start,
                astResult.end
            )
            is ParsingResult.Success -> astResult.node as ScriptNode
        }

        if (printAST) {
            ast.debugPrint()
            println("\n")
        }

        val ir = try {
            Transformer().transform(ast)
        } catch (e: Throwable) {
            return ExecutionResult.InternalError(e)
        }

        if (printIR) {
            IrPrinter(ir).print()
            println("\n")
        }

        val globalEnv = GlobalEnvRecord(realm, ast.scope.isStrict)
        realm.globalEnv = globalEnv
        realm.varEnv = globalEnv
        realm.lexEnv = globalEnv

        return try {
            val function = Interpreter.wrap(ir, realm)
            ExecutionResult.Success(function.call(realm.globalObject, emptyList()))
        } catch (e: ThrowException) {
            ExecutionResult.RuntimeError(realm, e.value)
        } catch (e: Throwable) {
            ExecutionResult.InternalError(e)
        } finally {
            processMicrotasks()
            realm.clearEnvRecords()
        }
    }

    fun addMicrotask(task: () -> Unit) {
        pendingMicrotasks.addFirst(task)
    }

    fun processMicrotasks() {
        while (pendingMicrotasks.isNotEmpty() && Reeva.running)
            pendingMicrotasks.removeLast()()
    }

    internal fun nextId() = uniqueId++
}
