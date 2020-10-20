package me.mattco.jsthing.ast.expressions

import me.mattco.jsthing.ast.*

object ImportMetaNode : NodeBase(), MetaPropertyNode

object NewTargetNode : NodeBase(), MetaPropertyNode

object TrueNode : NodeBase(), LiteralNode

object FalseNode : NodeBase(), LiteralNode
