package me.mattco.reeva.runtime

import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.*


class Inspection(val contents: String, val children: List<Inspection> = emptyList()) {
    private fun spacing(indent: Int) = "  ".repeat(indent)

    fun stringify(indent: Int = 0): String = buildString {
        append(spacing(indent), contents)
        children.forEach {
            append('\n', it.stringify(indent + 1))
        }
    }
}

fun buildInspection(builder: InspectionBuilder.() -> Unit): Inspection {
    val inspectionBuilder = InspectionBuilder()
    inspectionBuilder.builder()
    return inspectionBuilder.build()
}

class InspectionBuilder {
    private lateinit var value: String
    private val children = mutableListOf<Inspection>()

    fun contents(value: String) {
        this.value = value
    }

    fun child(value: String) {
        children.add(Inspection(value))
    }

    fun child(inspection: Inspection) {
        children.add(inspection)
    }

    fun child(builder: InspectionBuilder.() -> Unit) {
        children.add(buildInspection(builder))
    }

    fun child(value: String, builder: InspectionBuilder.() -> Unit) {
        val inspectionBuilder = InspectionBuilder()
        inspectionBuilder.value = value
        inspectionBuilder.builder()
        children.add(inspectionBuilder.build())
    }

    internal fun build() = Inspection(value, children)
}

fun inspect(value: JSValue, simple: Boolean): Inspection {
    return when (value) {
        JSEmpty -> buildInspection { contents("Type: Empty") }
        JSUndefined -> buildInspection { contents("Type: Undefined") }
        JSNull -> buildInspection { contents("Type: Null") }
        is JSTrue -> buildInspection { contents("Type: True") }
        is JSFalse -> buildInspection { contents("Type: False") }
        is JSNumber -> buildInspection {
            contents("Type: Number (${value.number}")
        }
        is JSString -> buildInspection {
            contents("Type: String (\"${value.string}\")")
        }
        is JSAccessor -> buildInspection {
            contents(buildString {
                append("Type: Accessor")
                if (value.getter != null)
                    append(" (Getter)")
                if (value.setter != null)
                    append(" (Setter")
            })
        }
        is JSNativeProperty -> buildInspection { contents("Type: NativeProperty") }
        is JSBigInt -> buildInspection {
            contents("Type: BigInt (${value.number.toString(10)})")
        }
        is JSSymbol -> buildInspection {
            contents("Type: Symbol (${value.description}, hash=${value.hashCode()})")
        }
        is JSObject -> if (simple) {
            buildInspection {
                val simpleName = if (value is JSNativeFunction) "JSNativeFunction" else value::class.simpleName
                contents("Type: Object ($simpleName)")
            }
        } else value.inspect()
        else -> TODO()
    }
}
