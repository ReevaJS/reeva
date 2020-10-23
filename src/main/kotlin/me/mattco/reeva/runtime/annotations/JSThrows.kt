package me.mattco.reeva.runtime.annotations

/**
 * Indicates that a method is capable of producing an error,
 * and any call of the method should be followed by a call
 * to Agent.checkError
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class JSThrows
