package me.mattco.reeva.runtime.jvmcompat

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.arrays.JSArrayObject
import me.mattco.reeva.runtime.builtins.JSMapObject
import me.mattco.reeva.runtime.builtins.JSSetObject
import me.mattco.reeva.runtime.primitives.JSNull
import me.mattco.reeva.runtime.primitives.JSNumber
import me.mattco.reeva.runtime.primitives.JSString
import me.mattco.reeva.runtime.primitives.JSUndefined

object JVMValueMapper {
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
