package com.reevajs.reeva.parsing

import com.reevajs.reeva.ast.*
import com.reevajs.reeva.ast.expressions.SuperCallExpressionNode
import com.reevajs.reeva.ast.literals.MethodDefinitionNode
import com.reevajs.reeva.ast.literals.PropertyName
import com.reevajs.reeva.ast.statements.BlockNode
import com.reevajs.reeva.parsing.lexer.TokenLocation
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.utils.expect

class EarlyErrorDetector(private val reporter: ErrorReporter) : ASTVisitor {
    override fun visit(node: ASTNode) {
        // Verify that we're setting node location properties correctly
        expect(node.sourceLocation.let { it.start != TokenLocation.EMPTY && it.end != TokenLocation.EMPTY })
        super.visit(node)
    }

    override fun visitScript(node: ScriptNode) {
        visitScope(node.scope)
        super.visitScript(node)
    }

    override fun visitModule(node: ModuleNode) {
        val seenNames = mutableSetOf<String>()

        node.body.filterIsInstance<ExportNode>().forEach { exportNode ->
            exportNode.exportEntries.forEach {
                if (it.exportName != null && !seenNames.add(it.exportName))
                    reporter.at(exportNode).duplicateExport(it.exportName)
            }
        }

        visitScope(node.scope)
        super.visitModule(node)
    }

    override fun visitBlock(node: BlockNode) {
        visitScope(node.scope)
        super.visitBlock(node)
    }

    override fun visitFunctionDeclaration(node: FunctionDeclarationNode) {
        visitScope(node.scope)
        super.visitFunctionDeclaration(node)
    }

    override fun visitFunctionExpression(node: FunctionExpressionNode) {
        visitScope(node.scope)
        super.visitFunctionExpression(node)
    }

    override fun visitArrowFunction(node: ArrowFunctionNode) {
        visitScope(node.scope)
        super.visitArrowFunction(node)
    }

    override fun visitMethodDefinition(node: MethodDefinitionNode) {
        visitScope(node.scope)
        super.visitMethodDefinition(node)
    }

    override fun visitClass(node: ClassNode) {
        if (node.heritage == null) {
            val ctor = node.body.firstOrNull {
                it is ClassMethodNode && it.isConstructor()
            }
            if (ctor != null) {
                val superCalls = ctor.childrenOfType<SuperCallExpressionNode>()
                if (superCalls.isNotEmpty())
                    reporter.at(superCalls[0]).baseClassSuperCall()
            }
        }

        for (element in node.body) {
            if (element is ClassFieldNode) {
                if (element.identifier.type == PropertyName.Type.Identifier) {
                    val name = (element.identifier.expression as IdentifierNode).processedName
                    if (name == "constructor" || (element.isStatic && name == "prototype"))
                        reporter.at(element.identifier).classFieldInvalidName(element.isStatic, name)
                }
            }
        }
    }

    private fun visitScope(scope: Scope) {
        val varNames = mutableMapOf<String, VariableSourceNode>()
        val lexNames = mutableMapOf<String, VariableSourceNode>()
        val funcNames = mutableMapOf<String, VariableSourceNode>()

        val decls = scope.variableSources.toMutableList()
        if (scope is HoistingScope)
            decls.addAll(scope.hoistedVariables)

        decls.sortBy { it.sourceLocation.start.index }

        decls.forEach {
            val name = it.name()

            if (it.type == VariableType.Var) {
                val isFunction = it is FunctionDeclarationNode
                if (isFunction) {
                    if (name in lexNames)
                        reporter.at(it).variableRedeclaration(name, "lexical", "function")

                    // This is a fun Early Error:
                    // If two function declarations exist for a scope, it is an error, but _only_ if that scope
                    // is a block scope (i.e. not the global scope or a top-level function scope). So...
                    //
                    //     function b() {}; function b() {};
                    //
                    // ...is totally fine, but...
                    //
                    //     { function b() {}; function b() {}; }
                    //
                    // ...is a duplicate declaration error.
                    //
                    // Also, the situation described above only occurs if the code is in strict mode. However,
                    // if either of the functions are not plain functions (generator, async, etc), then it is
                    // always an error regardless of strictness.
                    if (name in funcNames && it.scope == funcNames[name]!!.scope &&
                        it.scope != it.scope.outerHoistingScope
                    ) {
                        if (it.scope.isStrict) {
                            reporter.at(it).duplicateDeclaration(name, "function")
                        } else {
                            val thisFunc = it as FunctionDeclarationNode
                            val otherFunc = funcNames[name]!! as FunctionDeclarationNode

                            if (thisFunc.kind != Operations.FunctionKind.Normal ||
                                otherFunc.kind != Operations.FunctionKind.Normal
                            ) {
                                reporter.at(it).duplicateDeclaration(name, "function")
                            }
                        }
                    }

                    funcNames[name] = it
                } else {
                    if (name in lexNames)
                        reporter.at(it).variableRedeclaration(name, "lexical", "variable")
                    if (name in funcNames)
                        reporter.at(it).variableRedeclaration(name, "function", "lexical")
                    varNames[name] = it
                }
            } else {
                if (name in varNames)
                    reporter.at(it).variableRedeclaration(name, "variable", "lexical")
                if (name in lexNames)
                    reporter.at(it).duplicateDeclaration(name, "lexical")
                if (name in funcNames)
                    reporter.at(it).variableRedeclaration(name, "function", "lexical")
                lexNames[name] = it
            }
        }
    }
}
