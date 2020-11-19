package me.mattco.reeva.runtime.annotations

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class JSNativeAccessorSetter(
    val name: String,
    val attributes: String = "CeW"
)
