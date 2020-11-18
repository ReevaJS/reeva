package me.mattco.reeva.runtime.objects

import me.mattco.reeva.core.Realm
import me.mattco.reeva.utils.expect

class Shape {
    private val transitions = mutableMapOf<Transition, Shape>()
    private lateinit var propertyTable: MutableMap<JSObject.StringOrSymbol, PropertyData>

    private val previousShape: Shape?
    var prototype: JSObject?
        private set
    private val name: JSObject.StringOrSymbol?
    var propertyCount: Int
        private set

    private val attributes: Int
    private val transitionType: TransitionType
    val isUnique: Boolean
    val realm: Realm

    constructor(previousShape: Shape, name: JSObject.StringOrSymbol, attributes: Int, transitionType: TransitionType) {
        this.previousShape = previousShape
        this.name = name
        this.attributes = attributes
        this.transitionType = transitionType

        isUnique = false
        realm = previousShape.realm
        prototype = previousShape.prototype
        propertyCount = if (transitionType == TransitionType.Put) {
            previousShape.propertyCount + 1
        } else previousShape.propertyCount
    }

    constructor(previousShape: Shape, newProto: JSObject?) {
        this.previousShape = previousShape
        prototype = newProto
        realm = previousShape.realm
        transitionType = TransitionType.Prototype
        propertyCount = previousShape.propertyCount

        isUnique = false
        name = null
        attributes = 0
    }

    constructor(realm: Realm, prototype: JSObject?, isUnique: Boolean) {
        this.realm = realm
        this.prototype = prototype
        this.isUnique = isUnique

        previousShape = null
        name = null
        attributes = 0
        propertyCount = 0
        transitionType = TransitionType.Invalid
    }

    // For the empty shape
    constructor(realm: Realm) {
        this.realm = realm

        previousShape = null
        prototype = null
        name = null
        propertyCount = 0
        attributes = 0
        transitionType = TransitionType.Invalid
        isUnique = false
    }

    fun makePutTransition(name: JSObject.StringOrSymbol, attributes: Int): Shape {
        return transitions.getOrPut(Transition(name, attributes)) {
            Shape(this, name, attributes, TransitionType.Put)
        }
    }

    fun makeConfigureTransition(name: JSObject.StringOrSymbol, attributes: Int): Shape {
        return transitions.getOrPut(Transition(name, attributes)) {
            Shape(this, name, attributes, TransitionType.Configure)
        }
    }

    fun makePrototypeTransition(prototype: JSObject?) = Shape(this, prototype)

    fun addPropertyWithoutTransition(name: JSObject.StringOrSymbol, attributes: Int) {
        buildPropertyTable()
        if (propertyTable.put(name, PropertyData(propertyCount, attributes)) == null)
            propertyCount++
    }

    fun setPrototypeWithoutTransition(prototype: JSObject?) {
        this.prototype = prototype
    }

    fun makeUniqueClone(): Shape {
        val newShape = Shape(realm, prototype, isUnique = true)
        buildPropertyTable()
        // TODO: Why is this built when we just reassign it anyways?
        newShape.buildPropertyTable()
        newShape.propertyTable = propertyTable.toMutableMap()
        newShape.propertyCount = newShape.propertyTable.size
        return newShape
    }

    operator fun get(name: JSObject.StringOrSymbol): PropertyData? {
        return getPropertyTable()[name]
    }

    fun orderedPropertyTable(): List<Property> {
        return getPropertyTable().entries.sortedBy {
            it.value.offset
        }.map {
            Property(it.key, it.value.offset, it.value.attributes)
        }
    }

    fun removeUniqueShapeProperty(name: JSObject.StringOrSymbol, offset: Int) {
        expect(isUnique)
        expect(::propertyTable.isInitialized)
        if (propertyTable.remove(name) != null)
            propertyCount--
        for (data in propertyTable) {
            expect(data.value.offset != offset)
            if (data.value.offset > offset)
                data.value.offset--
        }
    }

    fun addUniqueShapeProperty(name: JSObject.StringOrSymbol, attributes: Int) {
        expect(isUnique)
        expect(::propertyTable.isInitialized)
        expect(name !in propertyTable)
        propertyTable[name] = PropertyData(propertyTable.size, attributes)
        propertyCount++
    }

    fun reconfigureUniqueShapeProperty(name: JSObject.StringOrSymbol, attributes: Int) {
        expect(isUnique)
        expect(::propertyTable.isInitialized)
        val data = propertyTable[name]
        expect(data != null)
        data.attributes = attributes
    }

    private fun getPropertyTable(): MutableMap<JSObject.StringOrSymbol, PropertyData> {
        buildPropertyTable()
        return propertyTable
    }

    private fun buildPropertyTable() {
        if (::propertyTable.isInitialized)
            return

        propertyTable = mutableMapOf()

        var nextOffset = 0
        val transitionChain = mutableListOf<Shape>()
        for (shape in generateSequence(previousShape) { it.previousShape }) {
            if (shape::propertyTable.isInitialized) {
                propertyTable = shape.propertyTable.toMutableMap()
                nextOffset = shape.propertyCount
                break
            }

            transitionChain.add(shape)
        }
        transitionChain.add(this)

        for (shape in transitionChain.asReversed()) {
            if (shape.name == null) {
                // prototype transition
                continue
            }

            if (shape.transitionType == TransitionType.Put) {
                propertyTable[shape.name] = PropertyData(nextOffset++, shape.attributes)
            } else if (shape.transitionType == TransitionType.Configure) {
                propertyTable[shape.name]!!.attributes = shape.attributes
            }
        }
    }

    data class PropertyData(var offset: Int, var attributes: Int)

    data class Property(val name: JSObject.StringOrSymbol, val offset: Int, val attributes: Int)

    data class Transition(val name: JSObject.StringOrSymbol, val attributes: Int)

    enum class TransitionType {
        Invalid,
        Put,
        Configure,
        Prototype,
    }

    companion object {
        const val PROPERTY_COUNT_TRANSITION_LIMIT = 100
    }
}
