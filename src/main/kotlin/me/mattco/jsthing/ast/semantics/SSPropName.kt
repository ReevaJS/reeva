package me.mattco.jsthing.ast.semantics

interface SSPropName {
    // Note: Return value of "null" corresponds to the spec's "empty"
    fun propName(): String?
}
