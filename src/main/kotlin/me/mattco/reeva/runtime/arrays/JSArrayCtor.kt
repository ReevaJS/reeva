package me.mattco.reeva.runtime.arrays

import me.mattco.reeva.core.Agent
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.*

class JSArrayCtor private constructor(realm: Realm) : JSNativeFunction(realm, "ArrayConstructor", 1) {
    init {
        isConstructable = true
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        val realNewTarget = newTarget.let {
            if (it is JSUndefined) {
                Agent.runningContext.function ?: JSUndefined
            } else it
        }

        val proto = Operations.getPrototypeFromConstructor(realNewTarget, realm.arrayProto)

        return when (arguments.size) {
            0 -> JSArrayObject.create(realm, proto)
            1 -> {
                val array = JSArrayObject.create(realm, proto)
                val lengthArg = arguments[0]
                val length = if (lengthArg.isNumber) {
                    val intLen = Operations.toUint32(lengthArg)
                    // TODO: The spec says "if intLen is not the same value as len...", does that refer to the
                    // operation SameValue? Or is it different?
                    if (!intLen.sameValue(lengthArg))
                        Errors.InvalidArrayLength(Operations.toPrintableString(lengthArg)).throwRangeError()
                    intLen.asInt
                } else {
                    array.set(0, lengthArg)
                    1
                }
                array.indexedProperties.setArrayLikeSize(length)
                array
            }
            else -> {
                val array = Operations.arrayCreate(arguments.size, proto)
                arguments.forEachIndexed { index, value ->
                    array.indexedProperties.set(array, index, value)
                }
                expect(array.indexedProperties.arrayLikeSize == arguments.size)
                array
            }
        }
    }

    @JSMethod("isArray", 1)
    fun isArray(thisValue: JSValue, arguments: JSArguments): JSValue {
        return Operations.isArray(arguments.argument(0)).toValue()
    }

    companion object {
        fun create(realm: Realm) = JSArrayCtor(realm).initialize()
        fun create(realm: Realm, length: Int) = JSArrayCtor(realm).initialize().also {
            it.indexedProperties.setArrayLikeSize(length)
        }
    }
}
