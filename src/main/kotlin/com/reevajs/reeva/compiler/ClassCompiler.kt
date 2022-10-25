package com.reevajs.reeva.compiler

import codes.som.koffee.ClassAssembly
import codes.som.koffee.MethodAssembly
import codes.som.koffee.assembleClass
import codes.som.koffee.insns.jvm.*
import codes.som.koffee.insns.sugar.*
import codes.som.koffee.modifiers.Modifiers
import codes.som.koffee.modifiers.final
import codes.som.koffee.modifiers.public
import com.reevajs.reeva.ast.literals.MethodDefinitionNode
import com.reevajs.reeva.compiler.generators.*
import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.jvmcompat.JSClassObject
import com.reevajs.reeva.jvmcompat.JVMValueMapper
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.JSObjectProto
import com.reevajs.reeva.runtime.objects.PropertyKey
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.transformer.opcodes.ClassFieldDescriptor
import com.reevajs.reeva.transformer.opcodes.ClassMethodDescriptor
import com.reevajs.reeva.utils.Error
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.key
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.lang.reflect.Modifier
import java.net.URLClassLoader

class ClassCompiler(
    val className: String,
    val superClass: Class<*>?,
    val interfaces: List<Class<*>>,
    fieldDescriptors: List<ClassFieldDescriptor>,
    methodDescriptors: List<ClassMethodDescriptor>,
) {
    val constructorDescriptor = methodDescriptors.first { it.isConstructor }
    val instanceMethodDescriptors = methodDescriptors.filter { !it.isStatic && !it.isConstructor }
    val staticMethodDescriptors = methodDescriptors.filter { it.isStatic }
    val instanceFieldDescriptors = fieldDescriptors.filter { !it.isStatic }
    val staticFieldDescriptors = fieldDescriptors.filter { it.isStatic }

    val classFields = superClass?.fields.orEmpty()
    val classMethods = superClass?.methods.orEmpty()

    val implClassName = nextClassName(className + "JvmImpl")
    val implClassPath = "com/reevajs/reeva/generated/$implClassName"
    val ctorClassName = nextClassName(className + "Ctor")
    val ctorClassPath = "com/reevajs/reeva/generated/$ctorClassName"
    val protoClassName = nextClassName(className + "Proto")
    val protoClassPath = "com/reevajs/reeva/generated/$protoClassName"

    init {
        if (superClass != null && Modifier.isFinal(superClass.modifiers))
            Errors.JVMClass.FinalClass(superClass.name)

        fieldDescriptors.forEach { desc ->
            if (!desc.key.isString)
                return@forEach

            val matchingField = classFields.find { it.name == desc.key.asString }

            if (matchingField != null)
                Errors.JVMClass.ConflictingField(className, matchingField.name).throwTypeError()
        }

        methodDescriptors.forEach { desc ->
            if (!desc.key.isString || desc.isConstructor)
                return@forEach

            val matchingMethod = classMethods.find { it.name == desc.key.asString } ?: return@forEach

            if (!Modifier.isAbstract(matchingMethod.modifiers))
                Errors.JVMClass.ConflictingNonAbstractMethod(className, matchingMethod.name)

            if (Modifier.isStatic(matchingMethod.modifiers) != desc.isStatic) {
                Errors.JVMClass.ConflictingStaticInstanceMethod(
                    desc.isStatic, className, matchingMethod.name,
                ).throwTypeError()
            }
        }
    }

    /**
     * Generates a JVM class which implements the given ClassNode. Returns the fully
     * qualified name of the generated class, suitable for passing to Class.forName.
     */
    fun compile(): JSValue {
        val realm = Agent.activeAgent.getActiveRealm()
        val cl = CompilerClassLoader()

        InstanceGenerator(this).generate().also(cl::load)
        val protoClass = PrototypeGenerator(this).generate().let(cl::load)
        val ctorClass = ConstructorGenerator(this).generate().let(cl::load)

        val protoCtor = protoClass.declaredConstructors.single()
        val proto = protoCtor.newInstance(realm) as JSObject
        (proto as GeneratedObjectPrototype).setInstanceMethodKeys(instanceMethodDescriptors.map { it.key })
        proto.init()

        val ctorCtor = ctorClass.declaredConstructors.single()
        val ctor = ctorCtor.newInstance(realm, proto) as JSFunction
        (ctor as GeneratedObjectConstructor).setStaticFieldKeys(staticFieldDescriptors.map { it.key })
        ctor.setStaticFieldKeys(staticFieldDescriptors.map { it.key })
        ctor.setStaticMethodKeys(staticMethodDescriptors.map { it.key })
        ctor.init()

        AOs.makeClassConstructor(ctor)
        AOs.makeConstructor(ctor, false, proto)

        // Note: This isn't a base class in the strictest sense, however since we don't have a 
        //       parent _JavaScript_ class, we must treat it like a base class. The super class
        //       is responsible for initializing the returned object, but since we do that here
        //       ourselves, we are the JS base class.
        ctor.constructorKind = JSFunction.ConstructorKind.Base
        
        ctor.setPrototype(superClass?.let { JSClassObject.create(superClass) } ?: realm.functionProto)
        AOs.makeMethod(ctor, proto)
        AOs.createMethodProperty(proto, "constructor".key(), ctor)

        return ctor
    }

    companion object {
        private var counter = 0

        private fun nextClassName(name: String) = "${name}_${counter++}"
    }
}
