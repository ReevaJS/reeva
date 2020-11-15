package me.mattco.reeva.test262

import me.mattco.reeva.Reeva
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.arrays.JSArrayObject
import me.mattco.reeva.jvmcompat.JSClassObject
import me.mattco.reeva.jvmcompat.JVMValueMapper
import me.mattco.reeva.runtime.primitives.*
import me.mattco.reeva.utils.toValue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.*
import java.lang.reflect.ParameterizedType

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JVMValueMapperTests {
    init {
        Reeva.setup()
    }

    val realm = Reeva.makeRealm().also { it.initObjects() }

    @AfterAll
    fun teardown() {
        Reeva.teardown()
    }

    @Test
    fun `JSUndefined maps correctly to JVM types`() {
        expectThat(JSUndefined.toType<String>())
            .isA<String>()
            .isEqualTo("undefined")

        expectThat(JSUndefined.toType<Any>())
            .isNull()

        expectThat(JSUndefined.toType<Map<*, *>>())
            .isNull()
    }

    @Test
    fun `JSBoolean maps correctly to JVM types`() {
        expectThat(JSTrue.toType<Boolean>())
            .isA<Boolean>()
            .isTrue()

        expectThat(JSFalse.toType<Boolean>())
            .isA<Boolean>()
            .isFalse()

        expectThat(JSTrue.toType<Any>())
            .isA<Boolean>()
            .isTrue()

        expectThat(JSFalse.toType<Any>())
            .isA<Boolean>()
            .isFalse()

        expectThat(JSTrue.toType<String>())
            .isA<String>()
            .isEqualTo("true")

        expectThat(JSFalse.toType<String>())
            .isA<String>()
            .isEqualTo("false")

        expectThrows<Exception> {
            JSFalse.toType<Array<*>>()
        }

        expectThrows<Exception> {
            JSTrue.toType<Double>()
        }
    }

    @Test
    fun `JSNumber maps correctly to JVM types`() {
        val underlyingNumber = 5.0
        val number = JSNumber(underlyingNumber)

        expectThat(number.toType<Double>())
            .isA<Double>()
            .isEqualTo(underlyingNumber)

        expectThat(number.toType<Any>())
            .isA<Double>()
            .isEqualTo(underlyingNumber)

        // TODO: Need more tests for -inf, +inf, etc.
        expectThat(number.toType<Float>())
            .isA<Float>()
            .isEqualTo(underlyingNumber.toFloat())

        expectThat(number.toType<String>())
            .isA<String>()
            .contains("5")

        expectThrows<Exception> {
            number.toType<Boolean>()
        }
    }

    @Nested
    inner class `JSString maps correctly to JVM types` {
        val underlyingString = "My string"
        val jsString = JSString(underlyingString)

        val numberString = "5.1"
        val jsNumberString = JSString(numberString)

        val singleCharString = "e"
        val jsCharString = JSString(singleCharString)

        @Test
        fun `Maps correctly to String & Any`() {
            expectThat(jsString.toType<String>())
                .isA<String>()
                .isEqualTo(underlyingString)

            expectThat(jsString.toType<Any>())
                .isA<String>()
                .isEqualTo(underlyingString)
        }

        @Test
        fun `Maps correctly to number types`() {
            expectThat(jsNumberString.toType<Double>())
                .isA<Double>()
                .isEqualTo(numberString.toDouble())

            expectThat(jsString.toType<Double>())
                .isA<Double>()
                .isEqualTo(Double.NaN)
        }

        @Test
        fun `Maps correctly to Char type`() {
            expectThat(jsCharString.toType<Char>())
                .isA<Char>()
                .isEqualTo(singleCharString[0])

            expectThat(jsNumberString.toType<Char>())
                .isA<Char>()
                .isEqualTo(numberString.toDouble().toChar())

            expectThrows<Exception> {
                jsString.toType<Char>()
            }
        }

        @Test
        fun `Fails on invalid types`() {
            expectThrows<Exception> {
                jsString.toType<Boolean>()
            }

            expectThrows<Exception> {
                jsString.toType<Array<*>>()
            }
        }
    }

    @Test
    fun `JSNull maps correctly to JVM types`() {
        expectThat(JSNull.toType<String>())
            .isNull()

        expectThat(JSNull.toType<Any>())
            .isNull()

        expectThrows<Exception> {
            JVMValueMapper.coerceValueToType(JSNull, Double::class.javaPrimitiveType!!)
        }
    }

    @Nested
    inner class `JS JVM values map correctly to JVM types` {
        val testClass = JSClassObject.create(realm, TestClass::class.java)
        val testClassInstance = testClass.construct(emptyList(), testClass)

        val numberLikeClass = JSClassObject.create(realm, NumberLikeClass::class.java)
        val numberLikeInstance = numberLikeClass.construct(emptyList(), numberLikeClass)

        @Test
        fun `JSClassObject maps correctly to JVM types`() {
            expectThat(testClass.toType<Class<*>>())
                .isA<Class<*>>()
                .isEqualTo(TestClass::class.java)

            expectThat(testClass.toType<Any>())
                .isA<Class<*>>()
                .isEqualTo(TestClass::class.java)

            expectThat(testClass.toType<String>())
                .isA<String>()
                .isEqualTo(TestClass::class.java.toString())

            expectThrows<Exception> {
                testClass.toType<TestClass>()
            }

            expectThrows<Exception> {
                numberLikeClass.toType<Double>()
            }
        }

        @Test
        fun `JSClassInstanceObject maps correctly to JVM types`() {
            expectThat(testClassInstance.toType<String>())
                .isA<String>()

            expectThat(numberLikeInstance.toType<String>())
                .isA<String>()

            expectThat(testClassInstance.toType<Double>())
                .isA<Double>()
                .isEqualTo(Double.NaN)

            expectThat(numberLikeInstance.toType<Double>())
                .isA<Double>()
                .isEqualTo(NumberLikeClass().toString().toDouble())

            expectThrows<Exception> {
                numberLikeInstance.toType<Boolean>()
            }
        }
    }

    @Test
    fun `JSArrayObject maps correctly to JVM types`() {
        val array = JSArrayObject.create(realm)
        array.set(0, 1.0.toValue())
        array.set(1, 3.0.toValue())
        array.set(2, "5.0".toValue())
        val doubleGenericInfo = (TestClass::class.java.declaredMethods.find { it.name == "doubleListConsumer" }!!
            .genericParameterTypes.first() as ParameterizedType).actualTypeArguments
        val booleanGenericInfo = (TestClass::class.java.declaredMethods.find { it.name == "booleanListConsumer" }!!
            .genericParameterTypes.first() as ParameterizedType).actualTypeArguments

        expectThat(array.toType<String>())
            .isA<String>()
            .contains("1")
            .contains("3")
            .contains("5")

        expectThat(array.toType<Any>())
            .isA<Array<Any>>()

        expectThat(array.toType<Array<Double>>())
            .isA<Array<Double>>()

        expectThat(JVMValueMapper.coerceValueToType(array, List::class.java, genericInfo = doubleGenericInfo))
            .isA<List<Double>>()

        expectThrows<Exception> {
            JVMValueMapper.coerceValueToType(array, List::class.java, genericInfo = booleanGenericInfo)
        }

        expectThrows<Exception> {
            array.toType<Array<Boolean>>()
        }

        expectThrows<Exception> {
            array.toType<Double>()
        }
    }

    @Test
    fun `JSObject maps correctly to JVM types`() {
        // TODO
    }

    class TestClass {
        fun doubleListConsumer(list: List<Double>) {}
        fun booleanListConsumer(list: List<Boolean>) {}
    }

    class NumberLikeClass {
        override fun toString(): String {
            return "5.0"
        }
    }

    private inline fun <reified T> JSValue.toType(): Any? = JVMValueMapper.coerceValueToType(this, T::class.java)
}
