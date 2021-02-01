package me.mattco.reeva.ast

import me.mattco.reeva.ast.statements.StatementList

class ScriptNode(val statementList: StatementList) : NodeWithScope(statementList)
