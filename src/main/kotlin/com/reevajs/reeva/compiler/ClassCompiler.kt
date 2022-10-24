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
    private val className: String,
    private val superClass: Class<*>?,
    private val interfaces: List<Class<*>>,
    fieldDescriptors: List<ClassFieldDescriptor>,
    methodDescriptors: List<ClassMethodDescriptor>,
) {
    private val constructorDescriptor = methodDescriptors.first { it.isConstructor }
    private val instanceMethodDescriptors = methodDescriptors.filter { !it.isStatic && !it.isConstructor }
    private val staticMethodDescriptors = methodDescriptors.filter { it.isStatic }
    private val instanceFieldDescriptors = fieldDescriptors.filter { !it.isStatic }
    private val staticFieldDescriptors = fieldDescriptors.filter { it.isStatic }

    private val classFields = superClass?.fields.orEmpty()
    private val classMethods = superClass?.methods.orEmpty()

    private val implClassName = nextClassName(className + "JvmImpl")
    private val implClassPath = "com/reevajs/reeva/generated/$implClassName"
    private val ctorClassName = nextClassName(className + "Ctor")
    private val ctorClassPath = "com/reevajs/reeva/generated/$ctorClassName"
    private val protoClassName = nextClassName(className + "Proto")
    private val protoClassPath = "com/reevajs/reeva/generated/$protoClassName"

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

        createImplClass()
        val protoClass = createProtoClass()
        val ctorClass = createCtorClass()

        val protoCtor = protoClass.declaredConstructors.single()
        val proto = protoCtor.newInstance(realm) as JSObject
        (proto as GeneratedObjectPrototype).setInstanceMethodKeys(instanceMethodDescriptors.map { it.key })
        proto.init()

        val ctorCtor = ctorClass.declaredConstructors.single()
        val ctor = ctorCtor.newInstance(realm, proto) as JSFunction
        (ctor as GeneratedObjectConstructor).setStaticFieldKeys(staticFieldDescriptors.map { it.key })
        ctor.setStaticFieldKeys(staticMethodDescriptors.map { it.key })
        ctor.init()

        AOs.makeClassConstructor(ctor)
        AOs.makeConstructor(ctor, false, proto)

        if (superClass != null) {
            ctor.constructorKind = JSFunction.ConstructorKind.Derived
            ctor.setPrototype(JSClassObject.create(superClass))
        }

        AOs.makeMethod(ctor, proto)
        AOs.createMethodProperty(proto, "constructor".key(), ctor)

        return ctor
    }

    // TODO: This should be cached based on the values of superClass and interfaces
    private fun createImplClass(): Class<*> {
        fun MethodAssembly.generateCommonCtorImpl() {
            // this.realm = realm;
            aload_0
            aload_1
            putfield(implClassPath, "realm", Realm::class)

            // this.wrapper = wrapper;
            aload_0
            aload_2
            putfield(implClassPath, "wrapper", JSObject::class)

            _return
        }

        val clazz = assembleClass(
            public + final,
            implClassPath,
            superClass = superClass ?: Object::class.java,
            interfaces = interfaces,
        ) {
            field<Realm>(private + final, "realm")
            field<JSObject>(private + final, "wrapper")

            // Generate a constructor for every constructor in the super class

            val superClass = this@ClassCompiler.superClass
            val interfaces = this@ClassCompiler.interfaces
            val constructors = superClass?.constructors

            if (constructors == null) {
                method(public, "<init>", void, Realm::class, JSObject::class) {
                    aload_0
                    invokespecial<Any>("<init>", void)
                    generateCommonCtorImpl()
                }
            } else {
                for (constructor in constructors) {
                    method(
                        Modifiers(constructor.modifiers),
                        "<init>",
                        void,
                        Realm::class,
                        JSObject::class,
                        *constructor.parameterTypes,
                    ) {
                        aload_0

                        for (i in constructor.parameterTypes.indices)
                            loadType(Type.getType(constructor.parameterTypes[i]), i + 3)

                        invokespecial(
                            superClass.canonicalName!!.replace(".", "/"),
                            "<init>",
                            void,
                            *constructor.parameterTypes,
                        )

                        generateCommonCtorImpl()
                    }
                }
            }

            // TODO: Fields

            for (descriptor in instanceMethodDescriptors) {
                if (!descriptor.key.isString)
                    continue

                val methodName = descriptor.key.asString

                if (descriptor.kind != MethodDefinitionNode.Kind.Normal)
                    TODO()

                val targetMethods = listOfNotNull(superClass, *interfaces.toTypedArray()).flatMap { clazz ->
                    clazz.declaredMethods.filter {
                        it.name == methodName
                    }
                }

                // TODO: Optimize this out if targetMethods.size == 1?

                for (targetMethod in targetMethods) {
                    method(
                        public + final,
                        methodName,
                        targetMethod.returnType,
                        *targetMethod.parameterTypes,
                        exceptions = targetMethod.exceptionTypes.filterNotNull().toTypedArray()
                    ) {
                        aload_0

                        construct<JSArguments>()

                        // Insert receiver
                        dup
                        aload_0
                        getfield(implClassPath, "wrapper", JSObject::class)
                        invokevirtual<JSArguments>("add", Boolean::class, JSValue::class)
                        pop

                        // Insert new.target
                        dup
                        getstatic<JSUndefined>("INSTANCE", JSUndefined::class)
                        invokevirtual<JSArguments>("add", Boolean::class, JSValue::class)
                        pop

                        // Insert arguments
                        for (i in targetMethod.parameterTypes.indices) {
                            dup
                            aload(i + 1)
                            invokestatic<JVMValueMapper>("jvmToJS", JSValue::class, Any::class)
                            invokevirtual<JSArguments>("add", Boolean::class, JSValue::class)
                            pop
                        }

                        // Invoke implementation method
                        invokevirtual(implClassPath, methodName + "Impl", JSValue::class, JSArguments::class)

                        if (targetMethod.returnType == Void.TYPE) {
                            pop
                            _return
                            return@method
                        }

                        val returnType = Type.getType(targetMethod.returnType)
                        ldc(returnType)
                        invokestatic<JVMValueMapper>("jsToJvm", Any::class, JSValue::class, Class::class)
                        checkcast(returnType)

                        when (targetMethod.returnType) {
                            Int::class.java -> ireturn
                            Long::class.java -> lreturn
                            Float::class.java -> freturn
                            Double::class.java -> dreturn
                            else -> areturn
                        }
                    }
                }

                method(private + final, methodName + "Impl", JSValue::class, JSArguments::class) {
                    CompilerOpcodeVisitor(this, descriptor.functionInfo, implClassPath, Context.Impl).visitIR()
                }
            }
        }

        return CompilerClassLoader.load(clazz)
    }

    // TODO: This should be cached based on the values of superClass and interfaces
    private fun createCtorClass(): Class<*> {
        val length = constructorDescriptor.functionInfo.length

        val clazz = assembleClass(
            public + final,
            ctorClassPath,
            superClass = JSNativeFunction::class,
            interfaces = listOf(GeneratedObjectConstructor::class),
        ) {
            field<List<*>>(private, "staticFieldKeys")
            field<List<*>>(private, "staticMethodKeys")
            field<JSObject>(private, "associatedPrototype")

            method(public, "<init>", void, Realm::class, JSObject::class) {
                aload_0
                aload_1
                ldc(className)
                ldc(length)
                invokespecial<JSNativeFunction>("<init>", void, Realm::class, String::class, Int::class)

                aload_0
                aload_2
                putfield(ctorClassPath, "associatedPrototype", JSObject::class)

                _return
            }

            method(public + final, "setStaticFieldKeys", void, List::class) {
                aload_0
                aload_1
                putfield(ctorClassPath, "staticFieldKeys", List::class)
                _return
            }

            method(public + final, "setStaticMethodKeys", void, List::class) {
                aload_0
                aload_1
                putfield(ctorClassPath, "staticMethodKeys", List::class)
                _return
            }

            method(public, "init", void) {
                aload_0
                invokespecial<JSObject>("init", void)

                for ((index, descriptor) in staticFieldDescriptors.withIndex()) {
                    aload_0

                    aload_0
                    getfield(ctorClassPath, "staticFieldKeys", List::class)
                    ldc(index)
                    invokeinterface<List<*>>("get", Any::class, int)
                    checkcast<PropertyKey>()

                    if (descriptor.functionInfo != null) {
                        CompilerOpcodeVisitor(this, descriptor.functionInfo, ctorClassPath, Context.Ctor).visitIR()
                    } else {
                        pushUndefined
                    }

                    ldc(0)

                    invokevirtual(ctorClassPath, "defineOwnProperty", void, PropertyKey::class, JSValue::class, int)
                }

                for ((index, descriptor) in staticMethodDescriptors.withIndex()) {
                    generateMethodDefinition(
                        this@assembleClass,
                        this,
                        descriptor,
                        ctorClassPath,
                        index,
                        "staticMethodKeys",
                        Context.Ctor,
                    )
                }

                _return
            }

            val constructors = this@ClassCompiler.superClass?.declaredConstructors?.filter {
                Modifier.isPublic(it.modifiers) || Modifier.isProtected(it.modifiers)
            }?.groupBy { it.parameterCount }

            method(public, "evaluate", JSValue::class, JSArguments::class) {
                // "new" check
                aload_1
                invokevirtual<JSArguments>("getThisValue", JSValue::class)
                getstatic<JSUndefined>("INSTANCE", JSUndefined::class)
                ifStatement(JumpCondition.RefEqual) {
                    construct<Errors.CtorCallWithoutNew>(String::class) {
                        ldc(className)
                    }

                    invokevirtual<Error>("throwTypeError", Void::class.java)
                    pop
                }

                pushRealm
                aload_0
                getfield(ctorClassPath, "associatedPrototype", JSObject::class)
                invokestatic<JSObject>("create", JSObject::class, Realm::class, JSValue::class)
                dup
                val wrapper = astore()

                pushSlot("Impl")

                // Generate constructor call
                if (constructors == null) {
                    construct(implClassPath, Realm::class, JSObject::class) {
                        pushRealm
                        aload(wrapper)
                    }
                } else {
                    val constructor = constructors.values.singleOrNull()?.singleOrNull()
                        ?: TODO("Support multiple constructors")

                    construct(implClassPath, Realm::class, JSObject::class, *constructor.parameterTypes) {
                        pushRealm
                        aload(wrapper)

                        for ((index, param) in constructor.parameterTypes.withIndex()) {
                            val paramType = Type.getType(param)

                            aload_1
                            ldc(index)
                            invokevirtual<JSArguments>("argument", JSValue::class, int)
                            ldc(paramType)
                            invokestatic<JVMValueMapper>("jsToJvm", Any::class, JSValue::class, Class::class)
                            checkcast(paramType)
                        }
                    }
                }

                invokevirtual<JSObject>("setSlot", void, int, Any::class)

                // Invoke user constructor
                CompilerOpcodeVisitor(
                    this,
                    constructorDescriptor.functionInfo,
                    ctorClassPath,
                    Context.Ctor
                ).visitIR()
            }
        }

        return CompilerClassLoader.load(clazz)
    }

    private fun createProtoClass(): Class<*> {
        val clazz = assembleClass(
            public + final,
            protoClassPath,
            superClass = JSObject::class,
            interfaces = listOf(GeneratedObjectPrototype::class),
        ) {
            field<List<*>>(private, "instanceMethodKeys")

            method(public, "<init>", void, Realm::class) {
                aload_0
                aload_1
                dup
                invokevirtual<Realm>("getObjectProto", JSObjectProto::class)
                invokespecial<JSObject>("<init>", void, Realm::class, JSValue::class)
                _return
            }

            method(public + final, "setInstanceMethodKeys", void, List::class) {
                aload_0
                aload_1
                putfield(protoClassPath, "instanceMethodKeys", List::class)
                _return
            }

            fun MethodAssembly.getterSetterPrelude(fieldName: String) {
                aload_0
                checkcast<JSObject>()

                pushSlot("Impl")
                invokevirtual<JSObject>("getSlot", Any::class, int)

                dup
                instanceof(implClassPath)
                ifStatement(JumpCondition.False) {
                    pop

                    construct<Errors.JVMClass.IncompatibleFieldGet>(String::class, String::class) {
                        ldc(className)
                        ldc(fieldName)
                    }
                    invokevirtual<Error>("throwTypeError", Void::class.java)
                    pop

                    generateUnreachable()
                }

                checkcast(implClassPath)
            }

            method(public + final, "init", void) {
                aload_0
                invokespecial<JSObject>("init", void)

                for (field in this@ClassCompiler.superClass?.declaredFields.orEmpty()) {
                    if (Modifier.isStatic(field.modifiers) || field.name == "Companion")
                        continue

                    // receiver
                    aload_0

                    // Key
                    ldc(field.name)

                    // attributes
                    ldc(0)

                    // Getter
                    IndyUtils(this@assembleClass, this).generateGetter(field.name) {
                        getterSetterPrelude(field.name)
                        getfield(implClassPath, field.name, field.type)
                        boxIfNecessary(field.type)
                        invokestatic<JVMValueMapper>("jvmToJS", JSValue::class, Any::class)
                        areturn
                    }

                    // Setter
                    IndyUtils(this@assembleClass, this).generateSetter(field.name) {
                        if (Modifier.isFinal(field.modifiers)) {
                            construct<Errors.JVMClass.FinalFieldSet>(String::class, String::class) {
                                ldc(className)
                                ldc(field.name)
                            }
                            invokevirtual<Error>("throwTypeError", Void::class.java)
                            pop
                        } else {
                            getterSetterPrelude(field.name)

                            aload_2
                            ldc(Type.getType(field.type))
                            invokestatic<JVMValueMapper>("jsToJvm", Any::class, JSValue::class, Class::class)

                            putfield(implClassPath, field.name, field.type)
                        }

                        _return
                    }

                    invokevirtual<JSObject>(
                        "defineNativeProperty",
                        void,
                        String::class,
                        int,
                        Function1::class,
                        Function2::class,
                    )
                }

                for ((index, descriptor) in instanceMethodDescriptors.withIndex()) {
                    generateMethodDefinition(
                        this@assembleClass,
                        this,
                        descriptor,
                        protoClassPath,
                        index,
                        "instanceMethodKeys",
                        Context.Proto,
                    )
                }

                _return
            }
        }

        return CompilerClassLoader.load(clazz)
    }

    private fun generateMethodDefinition(
        classAssembly: ClassAssembly,
        methodAssembly: MethodAssembly,
        descriptor: ClassMethodDescriptor,
        thisClass: String,
        keyIndex: Int,
        keysField: String,
        context: Context,
    ) = with(methodAssembly) {
        with(classAssembly) {
            val isGetterSetter = descriptor.kind.let {
                it == MethodDefinitionNode.Kind.Getter || it == MethodDefinitionNode.Kind.Setter
            }

            if (descriptor.kind != MethodDefinitionNode.Kind.Normal && !isGetterSetter)
                TODO()

            // Receiver for call to define method
            aload_0

            // Property key
            aload_0
            getfield(thisClass, keysField, List::class)
            ldc(keyIndex)
            invokeinterface<List<*>>("get", Any::class, int)
            checkcast<PropertyKey>()

            if (!isGetterSetter)
                ldc(descriptor.functionInfo.length)

            // Function
            IndyUtils(classAssembly, methodAssembly).generateMethod(descriptor.key.toString()) {
                CompilerOpcodeVisitor(this, descriptor.functionInfo, classAssembly.name, context).visitIR()
            }

            // Set on receiver
            when (descriptor.kind) {
                MethodDefinitionNode.Kind.Normal -> invokevirtual(
                    thisClass,
                    "defineBuiltin",
                    void,
                    PropertyKey::class,
                    int,
                    Function1::class,
                )
                MethodDefinitionNode.Kind.Getter -> invokevirtual(
                    thisClass,
                    "defineBuiltinGetter",
                    void,
                    PropertyKey::class,
                    Function1::class,
                )
                MethodDefinitionNode.Kind.Setter -> invokevirtual(
                    thisClass,
                    "defineBuiltinSetter",
                    void,
                    PropertyKey::class,
                    Function1::class,
                )
                else -> TODO()
            }
        }
    }

    // TODO: New class loader for every generated class so they can be garbage collected
    private object CompilerClassLoader : URLClassLoader(emptyArray()) {
        fun load(node: ClassNode): Class<*> {
            val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
            node.accept(writer)
            val bytes = writer.toByteArray()

            Agent.activeAgent.compiledClassDebugDirectory?.let {
                File(it, node.name.takeLastWhile { it != '/' } + ".class").writeBytes(bytes)
            }

            node.name

            return defineClass(node.name.replace('/', '.'), bytes, 0, bytes.size)
        }
    }

    enum class Context {
        Impl,
        Proto,
        Ctor,
    }

    companion object {
        private var counter = 0

        private fun nextClassName(name: String) = "${name}_${counter++}"
    }
}
