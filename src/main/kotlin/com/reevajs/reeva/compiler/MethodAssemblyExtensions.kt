package com.reevajs.reeva.compiler

import codes.som.koffee.MethodAssembly
import codes.som.koffee.insns.jvm.*
import codes.som.koffee.insns.sugar.construct
import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.primitives.JSFalse
import com.reevajs.reeva.runtime.primitives.JSNull
import com.reevajs.reeva.runtime.primitives.JSTrue
import com.reevajs.reeva.runtime.primitives.JSUndefined

val MethodAssembly.pushNull: Unit
    get() = getstatic<JSNull>("INSTANCE", JSNull::class)

val MethodAssembly.pushUndefined: Unit
    get() = getstatic<JSUndefined>("INSTANCE", JSUndefined::class)

val MethodAssembly.pushTrue: Unit
    get() = getstatic<JSTrue>("INSTANCE", JSTrue::class)

val MethodAssembly.pushFalse: Unit
    get() = getstatic<JSFalse>("INSTANCE", JSFalse::class)

// TODO: Determine when there is an easier way to get the Realm, like from an argument
//       or the receiver object
val MethodAssembly.pushRealm: Unit
    get() {
        invokestatic<Agent>("getActiveAgent", Agent::class)
        invokevirtual<Agent>("getActiveRealm", Realm::class)
    }

fun MethodAssembly.generateUnreachable() {
    construct<IllegalStateException>(String::class) {
        ldc("unreachable")
    }
    athrow
}
