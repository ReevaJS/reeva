package me.mattco.reeva.ast.expressions

import me.mattco.reeva.ast.*

object ImportMetaNode : NodeBase(), MetaPropertyNode

object NewTargetNode : NodeBase(), MetaPropertyNode

object TrueNode : NodeBase(), LiteralNode

object FalseNode : NodeBase(), LiteralNode
