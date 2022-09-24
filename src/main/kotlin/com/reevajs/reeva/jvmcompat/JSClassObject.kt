package com.reevajs.reeva.jvmcompat

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.functions.JSRunnableFunction
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.toJSString
import com.reevajs.reeva.utils.*
import java.lang.reflect.Modifier

class JSClassObject private constructor(
    realm: Realm,
    val clazz: Class<*>,
) : JSNativeFunction(realm, clazz.name, 0) {
    val clazzProto = classProtoCache.getOrPut(clazz) { makeClassProto(clazz) }
    val className = clazz.name.replace('/', '.').replace("L", "").replace(";", "")

    override fun init() {
        super.init()

        defineOwnProperty("prototype", clazzProto, Descriptor.HAS_BASIC)
        defineBuiltin("toString", 0, ::toString)
    }

    override fun evaluate(_arguments: JSArguments): JSValue {
        val newTarget = _arguments.newTarget
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

        if (matchingCtors.isEmpty()) Errors.JVMClass.NoValidCtor(className, arguments.map { it.toJSString().string })
        if (matchingCtors.size > 1) Errors.JVMClass.AmbiguousCtors(className, arguments.map { it.toJSString().string })

        val targetCtor = matchingCtors[0]
        val mappedArguments = JVMValueMapper.coerceArgumentsToSignature(realm, targetCtor, arguments).toTypedArray()

        return JSClassInstanceObject.create(
            clazzProto,
            targetCtor.newInstance(*mappedArguments)
        )
    }

    private fun makeClassProto(clazz: Class<*>): JSObject {
        val obj = create(proto = realm.functionProto)

        obj.defineOwnProperty("constructor", this, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)

        clazz.declaredFields.forEach { field ->
            val isStatic = Modifier.isStatic(field.modifiers)

            val getter: NativeGetterSignature = { thisValue ->
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

                JVMValueMapper.jvmToJS(field.get(instance))
            }

            val setter: NativeSetterSignature? = if (Modifier.isFinal(field.modifiers)) {
                null
            } else { thisValue, value ->
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

                field.set(instance, JVMValueMapper.jsToJvm(value, field.type))

                JSUndefined
            }

            obj.defineNativeProperty(field.name.key(), Descriptor.DEFAULT_ATTRIBUTES, getter, setter)
        }

        clazz.declaredMethods.groupBy { it.name to Modifier.isStatic(it.modifiers) }.forEach { (key, availableMethods) ->
            val (name, isStatic) = key

            if (name.startsWith("access$") && name.endsWith("\$p")) {
                // seems to be a Kotlin magic method
                return@forEach
            }

            val nativeMethod: NativeFunctionSignature = { (arguments, thisValue) ->
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
                    Errors.JVMClass.NoValidMethod(className, arguments.map { it.toJSString().string })
                if (matchingMethods.size > 1)
                    Errors.JVMClass.AmbiguousMethods(className, arguments.map { it.toJSString().string })

                val targetMethod = matchingMethods[0]
                val mappedArguments = JVMValueMapper.coerceArgumentsToSignature(
                    realm,
                    targetMethod,
                    arguments,
                ).toTypedArray()

                val result = targetMethod.invoke(instance, *mappedArguments)
                JVMValueMapper.jvmToJS(result)
            }

            val function = JSRunnableFunction.create(
                name,
                availableMethods.minOf { it.parameterCount },
                function = nativeMethod,
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
        private val classCache = mutableMapOf<Class<*>, JSClassObject>()
        private val classProtoCache = mutableMapOf<Class<*>, JSObject>()

        fun create(clazz: Class<*>, realm: Realm = Agent.activeAgent.getActiveRealm()) = classCache.getOrPut(clazz) {
            JSClassObject(realm, clazz).initialize()
        }

        @JvmStatic
        fun toString(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            expect(thisValue is JSClassInstanceObject)
            return thisValue.obj.toString().toValue()
        }
    }
}
