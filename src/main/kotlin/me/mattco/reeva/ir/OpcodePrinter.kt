package me.mattco.reeva.ir

class OpcodePrinter(private val argCount: Int = 0) {
    fun stringifyOpcode(opcode: Opcode): String {
        return buildString {
            append(opcode::class.simpleName)
            opcode::class.java.declaredFields.filter {
                it.name != "INSTANCE"
            }.forEach {
                it.isAccessible = true
                append(' ')
                append(formatArgument(it.get(opcode) as Int, it.name))
            }
        }
    }

    private fun formatArgument(value: Int, fieldName: String) = fieldName.toLowerCase().let {
        when {
            "cp" in it -> "[$value]"
            "reg" in it -> if (value < argCount) {
                "a${argCount - value - 1}"
            } else "r${value - argCount}"
            else -> "#$value"
        }
    }
}
