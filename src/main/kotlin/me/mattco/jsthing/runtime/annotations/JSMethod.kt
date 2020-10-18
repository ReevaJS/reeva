package me.mattco.jsthing.runtime.annotations

import me.mattco.jsthing.runtime.values.nonprimitives.objects.Attributes

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class JSMethod(
    val name: String,
    val length: Int,
    val attributes: Int = Attributes.defaultAttributes
)
