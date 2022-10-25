package com.reevajs.reeva.compiler

import codes.som.koffee.MethodAssembly
import codes.som.koffee.insns.jvm.*
import codes.som.koffee.insns.sugar.construct
import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.LdcInsnNode

val MethodAssembly.pushNull: Unit
    get() = getstatic<JSNull>("INSTANCE", JSNull::class)

val MethodAssembly.pushUndefined: Unit
    get() = getstatic<JSUndefined>("INSTANCE", JSUndefined::class)

val MethodAssembly.pushEmpty: Unit
    get() = getstatic<JSEmpty>("INSTANCE", JSEmpty::class)

val MethodAssembly.pushTrue: Unit
    get() = getstatic<JSTrue>("INSTANCE", JSTrue::class)

val MethodAssembly.pushFalse: Unit
    get() = getstatic<JSFalse>("INSTANCE", JSFalse::class)

fun MethodAssembly.generateUnreachable() {
    construct<IllegalStateException>(String::class) {
        ldc("unreachable")
    }
    athrow
}

fun MethodAssembly.pushSlot(name: String) {
    getstatic<Slot<*>>("Companion", Slot.Companion::class)
    invokevirtual<Slot.Companion>("get$name", int)
}

fun MethodAssembly.loadType(type: Type, index: Int) {
    when (type.sort) {
        Type.VOID -> error("cannot ldc void")
        Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT -> iload(index)
        Type.LONG -> lload(index)
        Type.FLOAT -> fload(index)
        Type.DOUBLE -> dload(index)
        else -> aload(index)
    }
}

fun MethodAssembly.boxIfNecessary(clazz: Class<*>) {
    when (Type.getType(clazz).sort) {
        Type.VOID -> return
        Type.BOOLEAN -> invokestatic<Boolean>("valueOf", java.lang.Boolean::class, boolean)
        Type.CHAR -> invokestatic<Char>("valueOf", java.lang.Character::class, char)
        Type.BYTE -> invokestatic<Byte>("valueOf", java.lang.Byte::class, byte)
        Type.SHORT -> invokestatic<Short>("valueOf", java.lang.Short::class, short)
        Type.INT -> invokestatic<Int>("valueOf", java.lang.Integer::class, int)
        Type.LONG -> invokestatic<Long>("valueOf", java.lang.Long::class, long)
        Type.FLOAT -> invokestatic<Float>("valueOf", java.lang.Float::class, float)
        Type.DOUBLE -> invokestatic<Double>("valueOf", java.lang.Double::class, double)
        else -> return
    }
}
