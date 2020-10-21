package me.mattco.renva.runtime.values

import me.mattco.renva.runtime.annotations.ECMAImpl
import me.mattco.renva.runtime.values.objects.JSObject
import me.mattco.renva.runtime.values.primitives.JSBoolean
import me.mattco.renva.runtime.values.primitives.JSNumber
import me.mattco.renva.runtime.values.primitives.JSString
import me.mattco.renva.runtime.values.primitives.JSUndefined
import me.mattco.renva.utils.expect

open class JSReference(
    @JvmField @ECMAImpl("GetBase", "6.2.4.1")
    val baseValue: Ref,
    @JvmField @ECMAImpl("GetReferencedName", "6.2.4.2")
    val name: String,
    @JvmField @ECMAImpl("IsStrictReference", "6.2.4.3")
    val isStrict: Boolean
) : JSValue() {
    @ECMAImpl("HasPrimitiveBase", "6.2.4.4")
    val hasPrimitiveBase = when (baseValue) {
        // is JSBigInt,
        // is JSSymbol
        is JSBoolean,
        is JSString,
        is JSNumber -> true
        else -> false
    }

    @ECMAImpl("IsPropertyReference", "6.2.4.5")
    val isPropertyReference = when {
        baseValue is JSObject -> true
        hasPrimitiveBase -> true
        else -> false
    }

    @ECMAImpl("IsUnresolvableReference", "6.2.4.6")
    val isUnresolvableReference = baseValue == JSUndefined

    @ECMAImpl("IsSuperReference", "6.2.4.7")
    val isSuperReference by lazy { this is JSSuperReference }

    @ECMAImpl("GetThisValue", "6.2.4.10")
    fun getThisValue(): JSValue {
        expect(isPropertyReference)
        if (isSuperReference)
            return (this as JSSuperReference)._thisValue
        return baseValue as JSValue
    }
}

class JSSuperReference(
    baseValue: JSValue,
    name: String,
    isStrict: Boolean,
    val _thisValue: JSValue
) : JSReference(baseValue, name, isStrict) {

}
