package com.reevajs.reeva.runtime.annotations

/**
 * Simple annotation with denotes that the targeted method
 * is an implementation of a particular ECMAScript algorithm.
 *
 * As an example, an annotation for the implementation of the
 * NewFunctionEnvironment (https://tc39.es/ecma262/#sec-newfunctionenvironment)
 * operation would be @ECMAImpl("8.1.2.4", "NewFunctionEnvironment")
 *
 * Sometimes an operation does not have a name, as is the case with
 * section of Static and Runtime Semantics blocks. In this case, only the
 * section may be specified. In this case, multiple different algorithms
 * may share the same section tag.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS, AnnotationTarget.VALUE_PARAMETER)
@Repeatable
annotation class ECMAImpl(
    vararg val section: String,
    val spec: String = "https://262.ecma-international.org/13.0/",
)
