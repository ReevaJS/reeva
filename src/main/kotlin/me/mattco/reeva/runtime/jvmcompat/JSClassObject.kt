package me.mattco.reeva.runtime.jvmcompat

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.*

class JSClassObject private constructor(realm: Realm, val clazz: Class<*>) : JSNativeFunction(realm, clazz.name, 0) {
    private val clazzProto by lazy { makeClassProto(clazz) }
    private val className = clazz.name.replace('/', '.').replace("L", "").replace(";", "")

    init {
        isConstructable = true
    }

    override fun call(thisValue: JSValue, arguments: JSArguments): JSValue {
        Errors.JVMClass.InvalidCall.throwTypeError()
    }

    override fun construct(arguments: JSArguments, newTarget: JSValue): JSValue {
        var ctors = clazz.constructors.toList()

        if (ctors.isEmpty())
            Errors.JVMClass.NoPublicCtors(className)

        val mappedArgs = arguments.map(JVMValueMapper::jsToJVM)

        // TODO: Deal with primitive number coercion
        // Right now, jsToJVM only converts JSNumber to Double,
        // and obviously that makes it impossible to call methods
        // that take non-double numeric arguments

        for (i in arguments.indices) {
            // TODO: Handle Kotlin non-null args?
            val argument = mappedArgs[i] ?: continue

            ctors = ctors.filter { it.parameterTypes[i] == argument::class.java }

            // TODO: Print valid ctors?
            if (ctors.isEmpty())
                Errors.JVMClass.NoValidCtor(className, mappedArgs.map(::printableClassName))
        }

        if (ctors.size > 1)
            Errors.JVMClass.AmbiguousCtors(className, mappedArgs.map(::printableClassName))

        return JSClassInstanceObject.create(
            realm,
            clazzProto,
            ctors[0].newInstance(*mappedArgs.toTypedArray())
        )
    }

    @JSMethod("toString", 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun toString(thisValue: JSValue, arguments: JSArguments): JSValue {
        return "Class(${clazz.name})".toValue()
    }

    private fun printableClassName(value: Any?) = when (value) {
        null -> "null"
        is Byte -> "byte"
        is Short -> "short"
        is Int -> "int"
        is Float -> "float"
        is Double -> "double"
        is Long -> "long"
        is String -> "String"
        is JSValue -> "JSValue"
        is JSObject -> "JSObject"
        else -> value::class.java.name
    }

    private fun makeClassProto(clazz: Class<*>): JSObject {
        val obj = create(realm)

        clazz.declaredFields.forEach { field ->
            val getter = { thisValue: JSValue ->
                if (thisValue !is JSClassInstanceObject)
                    Errors.JVMClass.IncompatibleFieldSet(className, field.name).throwTypeError()

                val instance = thisValue.obj
                if (!clazz.isInstance(instance))
                    Errors.JVMClass.IncompatibleFieldSet(className, field.name).throwTypeError()

                JVMValueMapper.jvmToJS(realm, field.get(instance))
            }

            val setter: NativeSetterSignature = { thisValue: JSValue, value: JSValue ->
                if (thisValue !is JSClassInstanceObject)
                    Errors.JVMClass.IncompatibleFieldSet(className, field.name).throwTypeError()

                val instance = thisValue.obj
                if (!clazz.isInstance(instance))
                    Errors.JVMClass.IncompatibleFieldSet(className, field.name).throwTypeError()

                val jvmValue = JVMValueMapper.jsToJVM(value)
                when {
                    jvmValue == null -> field.set(instance, null)
                    field.type != jvmValue::class.java -> field.set(instance, jvmValue)
                    else -> Errors.JVMClass.InvalidFieldSet(className, field.name, field.type.name, jvmValue::class.java.name)
                }

                JSUndefined
            }

            obj.defineNativeProperty(field.name.key(), Descriptor.defaultAttributes, getter, setter)
        }

        if (clazz.declaredMethods.map { it.name }.distinct().size != clazz.declaredMethods.size) {
            TODO("handle overloaded methods")
        }

        clazz.declaredMethods.forEach { method ->
            val nativeMethod: NativeFunctionSignature = { thisValue, arguments ->
                // TODO: Argument validation
                if (thisValue !is JSClassInstanceObject)
                    Errors.JVMClass.IncompatibleMethodCall(className, method.name).throwTypeError()

                val instance = thisValue.obj
                if (!clazz.isInstance(instance))
                    Errors.JVMClass.IncompatibleMethodCall(className, method.name).throwTypeError()

                val mappedArgs = arguments.map(JVMValueMapper::jsToJVM)
                val result = method.invoke(instance, *mappedArgs.toTypedArray())
                JVMValueMapper.jvmToJS(realm, result)
            }

            obj.defineNativeFunction(method.name.key(), method.parameterCount, Descriptor.defaultAttributes, nativeMethod)
        }

        clazz.declaredClasses.forEach { innerClazz ->
            // TODO
        }

        return obj
    }

    companion object {
        fun create(realm: Realm, clazz: Class<*>) = JSClassObject(realm, clazz).also { it.init() }
    }
}
