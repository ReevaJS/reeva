package me.mattco.jsthing.ast.semantics

interface SSAssignmentTargetType {
    fun assignmentTargetType(): AssignmentTargetType

    enum class AssignmentTargetType {
        Simple,
        Invalid
    }
}
