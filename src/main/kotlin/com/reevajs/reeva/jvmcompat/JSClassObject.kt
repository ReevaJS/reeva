package com.reevajs.reeva.jvmcompat

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.builtins.Builtin
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.*
import java.lang.reflect.Modifier

class JSClassObject private constructor(realm: Realm, val clazz: Class<*>) : JSNativeFunction(realm, clazz.name, 0) {
    private val clazzProto = classProtoCache.getOrPut(clazz) { makeClassProto(clazz) }
    private val className = clazz.name.replace('/', '.').replace("L", "").replace(";", "")

    override fun init() {
        super.init()

        defineOwnProperty("prototype", clazzProto, Descriptor.HAS_BASIC)
        defineBuiltin("toString", 0, Builtin.ClassObjectToString)
    }

    override fun evaluate(_arguments: JSArguments): JSValue {
        val newTarget = _arguments.newTarget
        if (newTarget == JSUndefined)
            Errors.JVMClass.InvalidCall.throwTypeError(realm)

        val arguments = if (JVMProxyMarker::class.java in clazz.interfaces) {
            expect(newTarget is JSObject)
            listOf(newTarget.get("prototype")) + _arguments
        } else _arguments

        val ctors = clazz.constructors.toList()

        if (ctors.isEmpty())
            Errors.JVMClass.NoPublicCtors(className)

        val matchingCtors = JVMValueMapper.findMatchingSignature(ctors, arguments)

        if (matchingCtors.isEmpty())
            Errors.JVMClass.NoValidCtor(className, arguments.map { Operations.toString(realm, it).string })
        if (matchingCtors.size > 1)
            Errors.JVMClass.AmbiguousCtors(className, arguments.map { Operations.toString(realm, it).string })

        val targetCtor = matchingCtors[0]
        val mappedArguments = JVMValueMapper.coerceArgumentsToSignature(realm, targetCtor, arguments).toTypedArray()

        return JSClassInstanceObject.create(
            realm,
            clazzProto,
            targetCtor.newInstance(*mappedArguments)
        )
    }

    private fun makeClassProto(clazz: Class<*>): JSObject {
        val obj = create(realm)

        obj.defineOwnProperty("constructor", this, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)

        clazz.fields.forEach { field ->
            val isStatic = Modifier.isStatic(field.modifiers)

            val getter: NativeGetterSignature = { realm, thisValue ->
                val instance = if (isStatic) {
                    if (thisValue != this)
                        Errors.JVMClass.IncompatibleStaticFieldGet(className, field.name).throwTypeError(realm)

                    null
                } else {
                    if (thisValue !is JSClassInstanceObject)
                        Errors.JVMClass.IncompatibleFieldGet(className, field.name).throwTypeError(realm)

                    val instance = thisValue.obj
                    if (!clazz.isInstance(instance))
                        Errors.JVMClass.IncompatibleFieldGet(className, field.name).throwTypeError(realm)
                    instance
                }

                JVMValueMapper.jvmToJS(realm, field.get(instance))
            }

            val setter: NativeSetterSignature? = if (Modifier.isFinal(field.modifiers)) {
                null
            } else { realm, thisValue, value ->
                val instance = if (isStatic) {
                    if (thisValue != this)
                        Errors.JVMClass.IncompatibleStaticFieldSet(className, field.name).throwTypeError(realm)

                    null
                } else {
                    if (thisValue !is JSClassInstanceObject)
                        Errors.JVMClass.IncompatibleFieldSet(className, field.name).throwTypeError(realm)

                    val instance = thisValue.obj
                    if (!clazz.isInstance(instance))
                        Errors.JVMClass.IncompatibleFieldSet(className, field.name).throwTypeError(realm)
                    instance
                }

                field.set(instance, JVMValueMapper.coerceValueToType(realm, value, field.type))

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

            val nativeMethod: NativeFunctionSignature = { realm, (arguments, thisValue) ->
                val instance = if (isStatic) {
                    if (thisValue != this)
                        Errors.JVMClass.IncompatibleStaticMethodCall(className, name).throwTypeError(realm)

                    null
                } else {
                    if (thisValue !is JSClassInstanceObject)
                        Errors.JVMClass.IncompatibleMethodCall(className, name).throwTypeError(realm)

                    val instance = thisValue.obj
                    if (!clazz.isInstance(instance))
                        Errors.JVMClass.IncompatibleMethodCall(className, name).throwTypeError(realm)

                    instance
                }

                val matchingMethods = JVMValueMapper.findMatchingSignature(availableMethods, arguments)

                if (matchingMethods.isEmpty())
                    Errors.JVMClass.NoValidMethod(className, arguments.map { Operations.toString(realm, it).string })
                if (matchingMethods.size > 1)
                    Errors.JVMClass.AmbiguousMethods(className, arguments.map { Operations.toString(realm, it).string })

                val targetMethod = matchingMethods[0]
                val mappedArguments = JVMValueMapper.coerceArgumentsToSignature(realm, targetMethod, arguments).toTypedArray()

                val result = targetMethod.invoke(instance, *mappedArguments)
                JVMValueMapper.jvmToJS(realm, result)
            }

            val function = fromLambda(
                realm,
                name,
                availableMethods.minOf { it.parameterCount },
                nativeMethod
            )

            val receiver = if (isStatic) this else obj
            receiver.addProperty(name.key(), Descriptor(function, Descriptor.DEFAULT_ATTRIBUTES))
        }

        clazz.declaredClasses.forEach { innerClazz ->
            // TODO
        }

        return obj
    }

    companion object {
        private val classProtoCache = mutableMapOf<Class<*>, JSObject>()

        fun create(realm: Realm, clazz: Class<*>) = JSClassObject(realm, clazz).initialize()

        @JvmStatic
        fun toString(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            expect(thisValue is JSClassObject)
            return "Class(${thisValue.clazz.name})".toValue()
        }
    }
}
