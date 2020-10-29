package me.mattco.reeva.runtime.annotations

import me.mattco.reeva.runtime.objects.Descriptor

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class JSNativeAccessorSetter(
    val name: String,
    val attributes: Int = Descriptor.defaultAttributes
)
