package me.mattco.reeva.runtime.annotations

import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.objects.Descriptor

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class JSNativePropertyGetter(
    val name: String,
    val attributes: Int = Descriptor.defaultAttributes
)
