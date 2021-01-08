package me.mattco.reeva.jvmcompat

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.*
import java.lang.reflect.Modifier

class JSClassObject private constructor(realm: Realm, val clazz: Class<*>) : JSNativeFunction(realm, clazz.name, 0) {
    private val clazzProto = classProtoCache.getOrPut(clazz) { makeClassProto(clazz) }
    private val className = clazz.name.replace('/', '.').replace("L", "").replace(";", "")

    init {
        isConstructable = true
    }

    override fun init() {
        super.init()

        defineOwnProperty("prototype", clazzProto, Descriptor.HAS_BASIC)
        defineNativeFunction("toString", 0, ::toString)
    }

    override fun evaluate(_arguments: JSArguments): JSValue {
        val newTarget = super.newTarget
        if (newTarget == JSUndefined)
            Errors.JVMClass.InvalidCall.throwTypeError()

        val arguments = if (JVMProxyMarker::class.java in clazz.interfaces) {
            expect(newTarget is JSObject)
            listOf(newTarget.get("prototype")) + _arguments
        } else _arguments

        val ctors = clazz.constructors.toList()

        if (ctors.isEmpty())
            Errors.JVMClass.NoPublicCtors(className)

        val matchingCtors = JVMValueMapper.findMatchingSignature(ctors, arguments)

        if (matchingCtors.isEmpty())
            Errors.JVMClass.NoValidCtor(className, arguments.map { Operations.toString(it).string })
        if (matchingCtors.size > 1)
            Errors.JVMClass.AmbiguousCtors(className, arguments.map { Operations.toString(it).string })

        val targetCtor = matchingCtors[0]
        val mappedArguments = JVMValueMapper.coerceArgumentsToSignature(targetCtor, arguments).toTypedArray()

        return JSClassInstanceObject.create(
            realm,
            clazzProto,
            targetCtor.newInstance(*mappedArguments)
        )
    }

    private fun toString(thisValue: JSValue, arguments: JSArguments): JSValue {
        return "Class(${clazz.name})".toValue()
    }

    private fun makeClassProto(clazz: Class<*>): JSObject {
        val obj = create(realm)

        obj.defineOwnProperty("constructor", this, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)

        clazz.fields.forEach { field ->
            val isStatic = Modifier.isStatic(field.modifiers)

            val getter = { thisValue: JSValue ->
                val instance = if (isStatic) {
                    if (thisValue != this)
                        Errors.JVMClass.IncompatibleStaticFieldGet(className, field.name).throwTypeError()

                    null
                } else {
                    if (thisValue !is JSClassInstanceObject)
                        Errors.JVMClass.IncompatibleFieldGet(className, field.name).throwTypeError()

                    val instance = thisValue.obj
                    if (!clazz.isInstance(instance))
                        Errors.JVMClass.IncompatibleFieldGet(className, field.name).throwTypeError()
                    instance
                }

                JVMValueMapper.jvmToJS(realm, field.get(instance))
            }

            val setter: NativeSetterSignature? = if (Modifier.isFinal(field.modifiers)) {
                null
            } else { thisValue: JSValue, value: JSValue ->
                val instance = if (isStatic) {
                    if (thisValue != this)
                        Errors.JVMClass.IncompatibleStaticFieldSet(className, field.name).throwTypeError()

                    null
                } else {
                    if (thisValue !is JSClassInstanceObject)
                        Errors.JVMClass.IncompatibleFieldSet(className, field.name).throwTypeError()

                    val instance = thisValue.obj
                    if (!clazz.isInstance(instance))
                        Errors.JVMClass.IncompatibleFieldSet(className, field.name).throwTypeError()
                    instance
                }

                field.set(instance, JVMValueMapper.coerceValueToType(value, field.type))

                JSUndefined
            }

            obj.defineNativeProperty(field.name.key(), Descriptor.DEFAULT_ATTRIBUTES, getter, setter)
        }

        clazz.methods.groupBy { it.name to Modifier.isStatic(it.modifiers) }.forEach { (key, availableMethods) ->
            val (name, isStatic) = key

            if (name.startsWith("access$") && name.endsWith("\$p")) {
                // seems to be a Kotlin magic method
                return@forEach
            }

            val nativeMethod: NativeFunctionSignature = { thisValue, arguments ->
                val instance = if (isStatic) {
                    if (thisValue != this)
                        Errors.JVMClass.IncompatibleStaticMethodCall(className, name).throwTypeError()

                    null
                } else {
                    if (thisValue !is JSClassInstanceObject)
                        Errors.JVMClass.IncompatibleMethodCall(className, name).throwTypeError()

                    val instance = thisValue.obj
                    if (!clazz.isInstance(instance))
                        Errors.JVMClass.IncompatibleMethodCall(className, name).throwTypeError()

                    instance
                }

                val matchingMethods = JVMValueMapper.findMatchingSignature(availableMethods, arguments)

                if (matchingMethods.isEmpty())
                    Errors.JVMClass.NoValidMethod(className, arguments.map { Operations.toString(it).string })
                if (matchingMethods.size > 1)
                    Errors.JVMClass.AmbiguousMethods(className, arguments.map { Operations.toString(it).string })

                val targetMethod = matchingMethods[0]
                val mappedArguments = JVMValueMapper.coerceArgumentsToSignature(targetMethod, arguments).toTypedArray()

                val result = targetMethod.invoke(instance, *mappedArguments)
                JVMValueMapper.jvmToJS(realm, result)
            }

            val receiver = if (isStatic) this else obj
            receiver.defineNativeFunction(
                name,
                availableMethods.minOf { it.parameterCount },
                Descriptor.DEFAULT_ATTRIBUTES,
                nativeMethod
            )
        }

        clazz.declaredClasses.forEach { innerClazz ->
            // TODO
        }

        return obj
    }

    companion object {
        private val classProtoCache = mutableMapOf<Class<*>, JSObject>()

        fun create(realm: Realm, clazz: Class<*>) = JSClassObject(realm, clazz).initialize()
    }
}
