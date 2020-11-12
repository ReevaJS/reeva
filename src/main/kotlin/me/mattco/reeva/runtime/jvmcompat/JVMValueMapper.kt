package me.mattco.reeva.runtime.jvmcompat

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.arrays.JSArrayObject
import me.mattco.reeva.runtime.builtins.JSMapObject
import me.mattco.reeva.runtime.builtins.JSSetObject
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.*
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.toValue
import java.lang.reflect.Executable
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Based on the [https://www-archive.mozilla.org/js/liveconnect/lc3_method_overloading.html]
 * specification.
 */
object JVMValueMapper {
    const val CONVERSION_FAILURE = -1

    fun getConversionWeight(value: JSValue, type: Class<*>) = when (value) {
        is JSUndefined -> 1
        is JSBoolean -> {
            when (type) {
                Boolean::class.javaPrimitiveType -> 1
                Boolean::class.javaObjectType -> 2
                Any::class.java -> 3
                String::class.java -> 4
                else -> CONVERSION_FAILURE
            }
        }
        is JSNumber -> {
            when (type) {
                Double::class.javaPrimitiveType -> 1
                Double::class.javaObjectType -> 2
                Float::class.javaPrimitiveType, Float::class.javaObjectType -> 3
                Long::class.javaPrimitiveType, Long::class.javaObjectType -> 4
                Int::class.javaPrimitiveType, Int::class.javaObjectType -> 5
                Short::class.javaPrimitiveType, Short::class.javaObjectType -> 6
                Byte::class.javaPrimitiveType, Byte::class.javaObjectType -> 7
                Char::class.javaPrimitiveType, Char::class.javaObjectType ->
                    if (value.isNaN) CONVERSION_FAILURE else 8
                String::class.java -> 9
                Any::class.java -> 10
                else -> CONVERSION_FAILURE
            }
        }
        is JSString -> {
            when (type) {
                String::class.java -> 1
                Any::class.java -> 2
                Char::class.javaPrimitiveType, Char::class.javaObjectType -> 3
                Double::class.javaPrimitiveType, Double::class.javaObjectType -> 4
                Float::class.javaPrimitiveType, Float::class.javaObjectType -> 5
                Long::class.javaPrimitiveType, Long::class.javaObjectType -> 6
                Int::class.javaPrimitiveType, Int::class.javaObjectType -> 7
                Short::class.javaPrimitiveType, Short::class.javaObjectType -> 8
                Byte::class.javaPrimitiveType, Byte::class.javaObjectType -> 9
                else -> CONVERSION_FAILURE
            }
        }
        is JSNull -> {
            if (!type.isPrimitive)
                1
            else
                CONVERSION_FAILURE
        }
        is JSClassInstanceObject -> {
            val javaObject = value.obj
            if (type.isInstance(javaObject))
                1
            else when (type) {
                String::class.java -> 2
                Double::class.javaPrimitiveType, Double::class.javaObjectType -> 3
                Float::class.javaPrimitiveType, Float::class.javaObjectType -> 4
                Long::class.javaPrimitiveType, Long::class.javaObjectType -> 5
                Int::class.javaPrimitiveType, Int::class.javaObjectType -> 6
                Short::class.javaPrimitiveType, Short::class.javaObjectType -> 7
                Byte::class.javaPrimitiveType, Byte::class.javaObjectType -> 8
                Char::class.javaPrimitiveType, Char::class.javaObjectType -> 9
                else -> CONVERSION_FAILURE
            }
        }
        is JSClassObject -> {
            when (type) {
                Class::class.java -> 1
                Any::class.java -> 2
                String::class.java -> 3
                else -> CONVERSION_FAILURE
            }
        }
        is JSArrayObject -> {
            when {
                type.isArray -> 1
                List::class.java.isAssignableFrom(type) -> 2
                else -> when (type) {
                    Any::class.java -> 3
                    String::class.java -> 4
                    else -> CONVERSION_FAILURE
                }
            }
        }
        is JSObject -> {
            when (type) {
                Any::class.java -> 1
                String::class.java -> 2
                Double::class.javaPrimitiveType, Double::class.javaObjectType -> 3
                Float::class.javaPrimitiveType, Float::class.javaObjectType -> 4
                Long::class.javaPrimitiveType, Long::class.javaObjectType -> 5
                Int::class.javaPrimitiveType, Int::class.javaObjectType -> 6
                Short::class.javaPrimitiveType, Short::class.javaObjectType -> 7
                Byte::class.javaPrimitiveType, Byte::class.javaObjectType -> 8
                Char::class.javaPrimitiveType, Char::class.javaObjectType -> 9
                else -> CONVERSION_FAILURE
            }
        }
        else -> CONVERSION_FAILURE
    }

    @JvmOverloads
    fun coerceValueToType(value: JSValue, type: Class<*>, genericInfo: Array<Type>? = null): Any? = when (value) {
        is JSUndefined -> {
            if (type == String::class.java)
                "undefined"
            else
                null
        }
        is JSBoolean -> {
            when (type) {
                Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType, Any::class.java -> value.value
                String::class.java -> value.value.toString()
                else -> Errors.JVMCompat.InconvertibleType(value, type).throwTypeError()
            }
        }
        is JSNumber -> {
            when (type) {
                Double::class.javaPrimitiveType, Double::class.javaObjectType, Any::class.java -> value.number
                Float::class.javaPrimitiveType, Float::class.javaObjectType -> value.number.toFloat() // TODO: Perhaps these conversion algos need more fine tuning
                Long::class.javaPrimitiveType, Long::class.javaObjectType -> value.number.toLong()
                Int::class.javaPrimitiveType, Int::class.javaObjectType -> value.number.toInt()
                Short::class.javaPrimitiveType, Short::class.javaObjectType -> value.number.toInt().toShort()
                Byte::class.javaPrimitiveType, Byte::class.javaObjectType -> value.number.toInt().toByte()
                Char::class.javaPrimitiveType, Char::class.javaObjectType ->
                    if (value.isNaN) Errors.JVMCompat.InconvertibleType(value, type).throwTypeError()
                    else value.number.toInt().toChar()
                String::class.java -> Operations.numericToString(value).string
                else -> Errors.JVMCompat.InconvertibleType(value, type).throwTypeError()
            }
        }
        is JSString -> {
            when (type) {
                String::class.java, Any::class.java -> value.string
                Double::class.javaPrimitiveType, Double::class.javaObjectType, Float::class.javaPrimitiveType,
                Float::class.javaObjectType, Long::class.javaPrimitiveType, Long::class.javaObjectType,
                Int::class.javaPrimitiveType, Int::class.javaObjectType, Short::class.javaPrimitiveType,
                Short::class.javaObjectType, Byte::class.javaPrimitiveType, Byte::class.javaObjectType ->
                    coerceValueToType(Operations.toNumber(value), type)
                Char::class.javaPrimitiveType, Char::class.javaObjectType ->
                    if (value.string.length == 1) value.string[0] else coerceValueToType(Operations.toNumber(value), type)
                else -> Errors.JVMCompat.InconvertibleType(value, type).throwTypeError()
            }
        }
        is JSNull -> {
            if (!type.isPrimitive)
                null
            else
                Errors.JVMCompat.InconvertibleType(value, type).throwTypeError()
        }
        is JSClassInstanceObject -> {
            val javaObject = value.obj
            if (type.isInstance(javaObject))
                javaObject
            else when (type) {
                String::class.java -> javaObject.toString()
                Double::class.javaPrimitiveType, Double::class.javaObjectType, Float::class.javaPrimitiveType,
                Float::class.javaObjectType, Long::class.javaPrimitiveType, Long::class.javaObjectType,
                Int::class.javaPrimitiveType, Int::class.javaObjectType, Short::class.javaPrimitiveType,
                Short::class.javaObjectType, Byte::class.javaPrimitiveType, Byte::class.javaObjectType ->
                    coerceValueToType(javaObject.toString().toValue(), type)
                else -> Errors.JVMCompat.InconvertibleType(value, type).throwTypeError()
            }
        }
        is JSClassObject -> {
            when (type) {
                Class::class.java, Any::class.java -> value.clazz
                String::class.java -> value.clazz.toString()
                else -> Errors.JVMCompat.InconvertibleType(value, type).throwTypeError()
            }
        }
        is JSArrayObject -> {
            if (type == String::class.java) {
                Operations.toString(value).string
            } else if (List::class.java.isAssignableFrom(type)) {
                val listInstance: MutableList<Any?> = if (type == List::class.java) mutableListOf() else type.newInstance() as MutableList<Any?>
                val listType = genericInfo?.firstOrNull() as? Class<*> ?: Any::class.java

                value.indexedProperties.indices().forEach { index ->
                    listInstance.add(index, coerceValueToType(value.get(index), listType))
                }

                listInstance
            } else if (type.isArray || type == Any::class.java) {
                val arrayType = if (type.isArray) type.componentType else Any::class.java
                val constructedArray = java.lang.reflect.Array.newInstance(arrayType, value.getLength(value).asInt)
                value.indexedProperties.indices().forEach { index ->
                    java.lang.reflect.Array.set(constructedArray, index, coerceValueToType(value.get(index), arrayType))
                }
                constructedArray
            } else {
                Errors.JVMCompat.InconvertibleType(value, type).throwTypeError()
            }
        }
        is JSObject -> {
            when (type) {
                Any::class.java -> TODO("convert to map?")
                String::class.java -> Operations.toString(value).string
                Double::class.javaPrimitiveType, Double::class.javaObjectType, Float::class.javaPrimitiveType,
                Float::class.javaObjectType, Long::class.javaPrimitiveType, Long::class.javaObjectType,
                Int::class.javaPrimitiveType, Int::class.javaObjectType, Short::class.javaPrimitiveType,
                Short::class.javaObjectType, Byte::class.javaPrimitiveType, Byte::class.javaObjectType ->
                    coerceValueToType(
                        Operations.toPrimitive(value, Operations.ToPrimitiveHint.AsNumber),
                        type
                    )
                else -> Errors.JVMCompat.InconvertibleType(value, type).throwTypeError()
            }
        }
        else -> Errors.JVMCompat.InconvertibleType(value, type).throwTypeError()
    }

    fun <T : Executable> findMatchingSignature(executables: List<T>, arguments: JSArguments): List<T> {
        val weightedExecutables = executables.filter { it.parameterCount == arguments.size }.groupBy {
            val weights = it.parameterTypes.withIndex().map { (i, type) -> getConversionWeight(arguments[i], type) }

            if (weights.any { weight -> weight == CONVERSION_FAILURE })
                CONVERSION_FAILURE
            else
                weights.sum()
        }

        val minWeight = weightedExecutables.keys.filter { it != CONVERSION_FAILURE }.minOrNull() ?: return emptyList()
        return weightedExecutables.getValue(minWeight)
    }

    fun coerceArgumentsToSignature(signature: Executable, arguments: JSArguments): List<Any?> {
        val genericTypes = signature.genericParameterTypes

        return arguments.mapIndexed { index, jsValue ->
            val genericType = genericTypes[index]
            if (genericType is ParameterizedType)
                coerceValueToType(jsValue, genericType.rawType as Class<*>, genericInfo = genericType.actualTypeArguments)
            else
                coerceValueToType(jsValue, genericType as Class<*>)
        }
    }

    fun jvmToJS(realm: Realm, instance: Any?): JSValue {
        return when (instance) {
            null -> JSUndefined
            is String -> JSString(instance)
            is Double -> JSNumber(instance)
            is Number -> JSNumber(instance)
            is Map<*, *> -> {
                val jsMap = JSMapObject.create(realm)
                instance.forEach { (key, value) ->
                    val jsKey = jvmToJS(realm, key)
                    jsMap.mapData[jsKey] = jvmToJS(realm, value)
                    jsMap.keyInsertionOrder.add(jsKey)
                }
                jsMap
            }
            is Set<*> -> {
                val jsSet = JSSetObject.create(realm)
                instance.forEach { key ->
                    val jsKey = jvmToJS(realm, key)
                    jsSet.setData.add(jsKey)
                    jsSet.insertionOrder.add(jsKey)
                }
                jsSet
            }
            is Collection<*> -> {
                val jsArray = JSArrayObject.create(realm)
                instance.forEachIndexed { index, value ->
                    jsArray.set(index, jvmToJS(realm, value))
                }
                jsArray
            }
            is Package -> JSPackageObject.create(realm, instance.name)
            is Class<*> -> JSClassObject.create(realm, instance)
            else -> TODO()
        }
    }

    fun jsToJVM(value: JSValue): Any? {
        return when (value) {
            is JSUndefined, is JSNull -> null
            is JSString -> value.string
            is JSNumber -> value.number
            is JSMapObject -> {
                mutableMapOf<Any?, Any?>().also { map ->
                    value.mapData.forEach { key, value ->
                        map[jsToJVM(key)] = jsToJVM(value)
                    }
                }
            }
            is JSSetObject -> {
                mutableSetOf<Any?>().also { set ->
                    value.setData.forEach {
                        set.add(jsToJVM(it))
                    }
                }
            }
            is JSArrayObject -> {
                mutableListOf<Any?>().also { array ->
                    value.indexedProperties.indices().forEach { index ->
                        array.add(jsToJVM(value.get(index)))
                    }
                }
            }
            is JSPackageObject -> if (value.packageName == null) null else Package.getPackage(value.packageName)
            is JSClassObject -> value.clazz
            is JSClassInstanceObject -> value.obj
            else -> TODO()
        }
    }
}
