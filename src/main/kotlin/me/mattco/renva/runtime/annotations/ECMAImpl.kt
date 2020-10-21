package me.mattco.renva.runtime.annotations

/**
 * Simple annotation with denotes that the targeted method
 * is an implementation of a particular ECMAScript algorithm.
 *
 * As an example, an annotation for the implementation of the
 * NewFunctionEnvironment (https://tc39.es/ecma262/#sec-newfunctionenvironment)
 * operation would be @ECMAImpl("NewFunctionEnvironment", "8.1.2.4")
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class ECMAImpl(val name: String, val identifiers: String = "")
