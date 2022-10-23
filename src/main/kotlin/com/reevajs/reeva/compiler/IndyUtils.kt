package com.reevajs.reeva.compiler

import codes.som.koffee.MethodAssembly
import codes.som.koffee.ClassAssembly
import codes.som.koffee.insns.jvm.invokedynamic
import codes.som.koffee.modifiers.private
import codes.som.koffee.modifiers.static
import codes.som.koffee.types.void
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.collections.JSArguments
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.lang.invoke.CallSite
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.function.Function
import kotlin.reflect.KClass

// TODO: Use context-receiver when they are usable and don't generate a gradle error
class IndyUtils(private val classAssembly: ClassAssembly, private val methodAssembly: MethodAssembly) {
    private val METAFACTORY_HANDLE = Handle(
        Opcodes.H_INVOKESTATIC,
        "java/lang/invoke/LambdaMetafactory",
        "metafactory",
        MethodType.methodType(
            CallSite::class.java,
            MethodHandles.Lookup::class.java,
            String::class.java,
            MethodType::class.java,
            MethodType::class.java,
            MethodHandle::class.java,
            MethodType::class.java,
        ).toMethodDescriptorString(),
        false,
    )

    fun generateMethod(name: String, block: MethodAssembly.() -> Unit) {
        generateLambda(
            name,
            Function1::class.java,
            JSValue::class.java,
            JSArguments::class.java,
            block = block,
        )
    }

    fun generateGetter(name: String, block: MethodAssembly.() -> Unit) {
        generateLambda(
            "getter_$name",
            Function1::class.java,
            JSValue::class.java,
            JSValue::class.java,
            block = block,
        )
    }

    fun generateSetter(name: String, block: MethodAssembly.() -> Unit) {
        generateLambda(
            "setter_$name",
            Function2::class.java,
            Void.TYPE,
            JSValue::class.java,
            JSValue::class.java,
            block = block,
        )
    }

    private fun generateLambda(
        name: String,
        clazz: Class<*>,
        returnType: Class<*>,
        vararg parameterTypes: Class<*>,
        block: MethodAssembly.() -> Unit,
    ) {
        val methodName = "lambda$$name$${counter++}"
        val methodType = MethodType.methodType(returnType, parameterTypes)

        val generatedMethodHandle = Handle(
            Opcodes.H_INVOKESTATIC,
            classAssembly.node.name,
            methodName,
            methodType.toMethodDescriptorString(),
            false,
        )

        methodAssembly.invokedynamic(
            "invoke", // Kotlin's Function interface methods are all named invoke
            clazz,
            handle = METAFACTORY_HANDLE,
            args = arrayOf(
                Type.getType(methodType.erase().toMethodDescriptorString()),
                generatedMethodHandle,
                Type.getType(methodType.toMethodDescriptorString()),
            ),
        )

        classAssembly.method(
            private + static,
            methodName,
            returnType,
            *parameterTypes,
            routine = block,
        )
    }

    companion object {
        private var counter = 0
    }
}
