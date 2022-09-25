package com.reevajs.reeva.utils

@Suppress("UNCHECKED_CAST")
@JvmInline
value class Result<ErrorT, ValueT> private constructor(private val either: Either<ErrorT, ValueT>) {
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

    companion object {
        fun <E : Any?, V : Any?> error(value: E) = Result<E, V>(Either.left(value))
        fun <E : Any?, V : Any?> success(value: V) = Result<E, V>(Either.right(value))
    }
}
