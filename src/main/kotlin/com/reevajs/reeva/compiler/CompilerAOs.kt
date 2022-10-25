package com.reevajs.reeva.compiler

import com.reevajs.reeva.jvmcompat.JVMValueMapper
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.toJSString
import com.reevajs.reeva.runtime.primitives.JSString
import com.reevajs.reeva.utils.Errors
import java.lang.reflect.Modifier

object CompilerAOs {
    @JvmStatic
    fun constructSuper(arguments: List<JSValue>, implClass: Class<*>): Any {        
        val constructors = implClass.declaredConstructors.filter {
            Modifier.isPublic(it.modifiers) || Modifier.isProtected(it.modifiers)
        }

        val matchingCtors = JVMValueMapper.findMatchingSignature(constructors, arguments, 2)
        val types = arguments.map { (AOs.typeofOperator(it) as JSString).string }
        
        // TODO: Use a better class name for this
        if (matchingCtors.isEmpty()) 
            Errors.JVMClass.NoValidCtor(implClass.name, types).throwTypeError()
        if (matchingCtors.size > 1) 
            Errors.JVMClass.AmbiguousCtors(implClass.name, types).throwTypeError()

        val matchingCtor = matchingCtors.single()
        val mappedArguments = arguments.mapIndexed { index, value ->
            JVMValueMapper.jsToJvm(value, matchingCtor.parameterTypes[index])
        }
        return matchingCtor.newInstance(*mappedArguments.toTypedArray())
    }
}
