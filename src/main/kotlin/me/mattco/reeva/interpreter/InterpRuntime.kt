package me.mattco.reeva.interpreter

import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSString
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.expect

enum class InterpRuntime(val function: (List<JSValue>) -> JSValue) {
    ThrowConstReassignment(::throwConstReassignment),
    ThrowIfIteratorNextNotCallable(::throwIfIteratorNextNotCallable),
    ThrowIfIteratorReturnNotObject(::throwIfIteratorReturnNotObject)
}

private fun throwConstReassignment(arguments: List<JSValue>): JSValue {
    expect(arguments.size == 1)
    expect(arguments[0] is JSString)
    Errors.AssignmentToConstant(arguments[0].asString).throwTypeError()
}

private fun throwIfIteratorNextNotCallable(arguments: List<JSValue>): JSValue {
    expect(arguments.size == 1)
    if (!Operations.isCallable(arguments[0]))
        Errors.IterableBadNext.throwTypeError()
    return JSUndefined
}

private fun throwIfIteratorReturnNotObject(arguments: List<JSValue>): JSValue {
    expect(arguments.size == 1)
    if (arguments[0] !is JSObject)
        Errors.NonObjectIteratorReturn.throwTypeError()
    return JSUndefined
}
