package com.reevajs.reeva.compiler

import com.reevajs.reeva.runtime.objects.PropertyKey

interface GeneratedObjectConstructor {
    fun setStaticFieldKeys(fieldKeys: List<PropertyKey>)

    fun setStaticMethodKeys(methodKeys: List<PropertyKey>)
}

interface GeneratedObjectPrototype {
    fun setInstanceMethodKeys(methodKeys: List<PropertyKey>)
}
