package me.mattco.reeva.compiler.graph

import me.mattco.reeva.runtime.primitives.*

object NodeDescriptors {
    // Constant nodes
    val undefined = constant(JSUndefined)
    val null_ = constant(JSNull)
    val true_ = constant(JSTrue)
    val false_ = constant(JSFalse)
    val infinity = constant(JSNumber.POSITIVE_INFINITY)
    val negativeInfinity = constant(JSNumber.NEGATIVE_INFINITY)
    val nan = constant(JSNumber.NaN)

    private val intCache = mutableMapOf<Int, Node.Descriptor>()
    private val longCache = mutableMapOf<Long, Node.Descriptor>()
    private val doubleCache = mutableMapOf<Double, Node.Descriptor>()
    private val stringCache = mutableMapOf<String, Node.Descriptor>()

    val start = Node.Descriptor(NodeType.Start, 0, 0, 0, 0, 1, 0)
    val end = Node.Descriptor(NodeType.End, 0, 1, 0, 0, 0, 0)

    val load = Node.Descriptor(NodeType.PropertyLoad, 2, 1, 0, 1, 1, 0)
    val store = Node.Descriptor(NodeType.PropertyStore, 3, 1, 0, 0, 1, 0)
    val createArrayLiteral = Node.Descriptor(NodeType.CreateArrayLiteral, 0, 0, 0, 1, 0, 0)
    val staArrayLiteral = Node.Descriptor(NodeType.StaArrayLiteral, 3, 1, 0, 0, 1, 0)
    val objectLiteral = Node.Descriptor(NodeType.CreateObjectLiteral, 0, 0, 0, 1, 0, 0)

    private val opCache = mutableMapOf<NodeType, Node.Descriptor>()

    val stringAppend = Node.Descriptor(NodeType.StringAppend, 2, 1, 0, 1, 1, 0)
    val typeof_ = Node.Descriptor(NodeType.TypeOf, 1, 1, 0, 1, 1, 0)

    val deletePropertySloppy = Node.Descriptor(NodeType.DeletePropertySloppy, 2, 1, 0, 1, 1, 0)
    val deletePropertyStrict = Node.Descriptor(NodeType.DeletePropertyStrict, 2, 1, 0, 1, 1, 0)
    val ldaGlobal = Node.Descriptor(NodeType.LdaGlobal, 1, 1, 0, 1, 1, 0)

    fun int(n: Int) = intCache.getOrPut(n) { constant(JSNumber(n)) }

    fun long(n: Long) = longCache.getOrPut(n) { constant(JSNumber(n)) }

    fun double(n: Double) = doubleCache.getOrPut(n) { constant(JSNumber(n)) }

    fun string(str: String) = stringCache.getOrPut(str) { constant(JSString(str)) }

    fun <T> constant(value: T) = Node.DescriptorWithConst(NodeType.Constant, 0, 0, 0, 1, 0, 0, param = value)

    fun unaryOp(type: NodeType): Node.Descriptor {
        return opCache.getOrPut(type) {
            Node.Descriptor(type, 1, 1, 0, 1, 1, 0)
        }
    }

    fun binaryOp(type: NodeType): Node.Descriptor {
        return opCache.getOrPut(type) {
            Node.Descriptor(type, 2, 1, 0, 1, 1, 0)
        }
    }
}
