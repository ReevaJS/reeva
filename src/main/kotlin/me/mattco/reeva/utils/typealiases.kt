package me.mattco.reeva.utils

import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue

typealias NativeGetterSignature = (thisValue: JSValue) -> JSValue
typealias NativeSetterSignature = (thisValue: JSValue, value: JSValue) -> Unit
typealias NativeFunctionSignature = (arguments: JSArguments) -> JSValue
