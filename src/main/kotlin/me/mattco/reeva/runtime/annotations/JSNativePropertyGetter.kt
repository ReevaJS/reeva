package me.mattco.reeva.runtime.annotations

import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.objects.Attributes

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class JSNativePropertyGetter(
    val name: String,
    val attributes: Int = Attributes.defaultAttributes
)
typealias NativeGetterSignature = (thisValue: JSValue) -> JSValue
