package com.reevajs.reeva.runtime.builtins

import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.collections.JSArguments
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

interface Builtin {
    val handle: MethodHandle
    val debugName: String

    companion object {
        val METHOD_TYPE: MethodType =
            MethodType.methodType(JSValue::class.java, JSArguments::class.java)

        fun forClass(clazz: Class<*>, name: String, debugName: String = name) = object : Builtin {
            override val handle: MethodHandle = MethodHandles.publicLookup().findStatic(clazz, name, METHOD_TYPE)
            override val debugName = debugName

        }
    }
}
