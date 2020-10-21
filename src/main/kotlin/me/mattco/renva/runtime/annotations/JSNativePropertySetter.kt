package me.mattco.renva.runtime.annotations

import me.mattco.renva.runtime.values.objects.Attributes

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class JSNativePropertySetter(
    val name: String,
    val attributes: Int = Attributes.defaultAttributes
)
