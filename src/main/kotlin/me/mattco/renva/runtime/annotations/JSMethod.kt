package me.mattco.renva.runtime.annotations

import me.mattco.renva.runtime.values.objects.Attributes

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class JSMethod(
    val name: String,
    val length: Int,
    val attributes: Int = Attributes.defaultAttributes
)
