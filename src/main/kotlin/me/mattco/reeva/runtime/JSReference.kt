package me.mattco.reeva.runtime

import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.PropertyKey
import me.mattco.reeva.runtime.primitives.*
import me.mattco.reeva.utils.expect

open class JSReference(
    @JvmField @ECMAImpl("6.2.4.1", "GetBase")
    val baseValue: Ref,
    @JvmField @ECMAImpl("6.2.4.2", "GetReferencedName")
    val name: PropertyKey,
    @JvmField @ECMAImpl("6.2.4.3", "IsStrictReference")
    val isStrict: Boolean,
    @ECMAImpl("3.9", spec = "https://tc39.es/proposal-class-fields")
    val isPrivateReference: Boolean = false,
) : JSValue() {
    init {
        if (isPrivateReference)
            expect(name.isString)
    }

    @ECMAImpl("6.2.4.4")
    val hasPrimitiveBase = when (baseValue) {
        is JSBigInt,
        is JSSymbol,
        is JSBoolean,
        is JSString,
        is JSNumber -> true
        else -> false
    }

    @ECMAImpl("6.2.4.5")
    val isPropertyReference = when {
        baseValue is JSObject -> true
        hasPrimitiveBase -> true
        else -> false
    }

    @ECMAImpl("6.2.4.6")
    val isUnresolvableReference: Boolean
        get() = baseValue == JSUndefined

    @ECMAImpl("6.2.4.7")
    val isSuperReference : Boolean
        get() = this is JSSuperReference

    @ECMAImpl("6.2.4.10")
    fun getThisValue(): JSValue {
        expect(isPropertyReference)
        if (isSuperReference)
            return (this as JSSuperReference)._thisValue
        return baseValue as JSValue
    }
}

class JSSuperReference(
    baseValue: JSValue,
    name: PropertyKey,
    isStrict: Boolean,
    val _thisValue: JSValue
) : JSReference(baseValue, name, isStrict)
