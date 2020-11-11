package me.mattco.reeva.runtime.jvmcompat

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.annotations.JSMethod
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

    override fun call(thisValue: JSValue, arguments: JSArguments): JSValue {
        Errors.JVMClass.InvalidCall.throwTypeError()
    }

    override fun construct(arguments: JSArguments, newTarget: JSValue): JSValue {
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

    @JSMethod("toString", 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun toString(thisValue: JSValue, arguments: JSArguments): JSValue {
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

            val setter: NativeSetterSignature = { thisValue: JSValue, value: JSValue ->
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

                val jvmValue = JVMValueMapper.coerceValueToType(value, field.type)
                field.set(instance, jvmValue)

                JSUndefined
            }

            obj.defineNativeProperty(field.name.key(), Descriptor.defaultAttributes, getter, setter)
        }

        clazz.methods.groupBy { it.name to Modifier.isStatic(it.modifiers) }.forEach { (key, availableMethods) ->
            val (name, isStatic) = key

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
                name.key(),
                availableMethods.minOf { it.parameterCount },
                Descriptor.defaultAttributes,
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

        fun create(realm: Realm, clazz: Class<*>) = JSClassObject(realm, clazz).also { it.init() }
    }
}
