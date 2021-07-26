package com.reevajs.reeva.utils

typealias ResultT<V> = Result<Throwable, V>
typealias ResultAny<V> = Result<Any, V>
typealias ResultAnyN<V> = Result<Any?, V>

@Suppress("UNCHECKED_CAST")
inline class Result<ErrorT, ValueT> private constructor(private val either: Either<ErrorT, ValueT>) {
    val hasError: Boolean get() = either.isLeft
    val hasValue: Boolean get() = either.isRight

    fun error() = either.left
    fun value() = either.right

    fun <T> mapError(mapper: (ErrorT) -> T): Result<T, ValueT> {
        return Result(either.mapLeft(mapper))
    }

    fun <T> mapValue(mapper: (ValueT) -> T): Result<ErrorT, T> {
        return Result(either.mapRight(mapper))
    }

    fun errorOrNull() = either.leftOrNull()
    fun valueOrNull() = either.rightOrNull()

    fun errorOrDefault(defaultValue: ErrorT) = either.leftOrDefault(defaultValue)
    fun valueOrDefault(defaultValue: ValueT) = either.rightOrDefault(defaultValue)

    fun errorOrElse(producer: () -> ErrorT) = either.leftOrElse(producer)
    fun valueOrElse(producer: () -> ValueT) = either.rightOrElse(producer)

    fun <NewE, NewV> cast(): Result<NewE, NewV> {
        return if (hasValue) {
            success(value() as NewV)
        } else error(error() as NewE)
    }

    open class Failure(val value: Any)

    companion object {
        fun <E : Any?, V : Any?> error(value: E) = Result<E, V>(Either.left(value))
        fun <E : Any?, V : Any?> success(value: V) = Result<E, V>(Either.right(value))

        inline fun <E : Throwable, V : Any?> wrap(block: () -> V): Result<E, V> = try {
            success(block())
        } catch (e: Throwable) {
            error(e as E)
        }
    }
}

fun <E, T> Result<E, Result<E, T>>.flatten(): Result<E, T> {
    return if (hasValue) value() else Result.error(error())
}

//@Suppress("UNCHECKED_CAST")
//inline class Result<out T> private constructor(private val value: Any?) {
//    val isSuccess: Boolean get() = value !is Failure
//
//    val isFailure: Boolean get() = value is Failure
//
//    fun value(): T {
//        expect(isSuccess)
//        return value!! as T
//    }
//
//    fun failure(): Any {
//        expect(isFailure)
//        return (value as Failure).value
//    }
//
//    fun valueOrNull() = if (isFailure) null else value as T
//
//    fun failureOrNull() = (value as? Failure)?.value
//
//    fun <U> mapValue(mapper: (T) -> U): Result<U> {
//        return if (isSuccess) {
//            success(mapper(value!! as T))
//        } else this as Result<U>
//    }
//
//    fun <U> map(mapper: (Result<T>) -> U): U {
//        return mapper(this)
//    }
//
//    fun <U> to(): Result<U> {
//        return if (isFailure) this as Result<U> else success(value as U)
//    }
//
//    open class Failure(val value: Any)
//
//    companion object {
//        fun <T : Any?> success(value: T) = Result<T>(value)
//
//        fun <T : Any?> failure(value: Throwable) = Result<T>(Failure(value))
//
//        fun <T : Any?> failure(failure: Failure) = Result<T>(failure)
//
//        inline fun <T : Any?> wrap(block: () -> T) = try {
//            success(block())
//        } catch (e: Throwable) {
//            failure(e)
//        }
//    }
//}
