package me.mattco.renva.ast.expressions

import me.mattco.renva.ast.*

object ImportMetaNode : NodeBase(), MetaPropertyNode

object NewTargetNode : NodeBase(), MetaPropertyNode

object TrueNode : NodeBase(), LiteralNode

object FalseNode : NodeBase(), LiteralNode
