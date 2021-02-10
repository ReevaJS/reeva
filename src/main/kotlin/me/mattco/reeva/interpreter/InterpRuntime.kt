package me.mattco.reeva.interpreter

import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.primitives.JSString
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.utils.expect

enum class InterpRuntime(val function: (JSArguments) -> JSValue) {
    ThrowConstReassignment({
        expect(it.size == 1)
        expect(it[0] is JSString)
        Errors.AssignmentToConstant(it[0].asString).throwTypeError()
    })
}
