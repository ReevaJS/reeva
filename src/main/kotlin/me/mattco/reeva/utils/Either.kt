package me.mattco.reeva.utils

@Suppress("UNCHECKED_CAST")
inline class Either<L, R>(private val value: Any) {
    val isLeft: Boolean get() = value is Left
    val isRight: Boolean get() = value is Right

    val left: L
        get() {
            expect(isLeft, "Either.getLeft() called on right-sided Either")
            return (value as Left).value as L
        }

    val right: R
        get() {
            expect(isRight, "Either.getRight() called on left-sided Either")
            return (value as Right).value as R
        }

    fun <T> mapLeft(mapper: (L) -> T): Either<T, R> {
        return if (isLeft) left(mapper(left)) else Either(value)
    }

    fun <T> mapRight(mapper: (R) -> T): Either<L, T> {
        return if (isRight) right(mapper(right)) else Either(value)
    }

    fun leftOrNull() = if (isLeft) left else null
    fun rightOrNull() = if (isRight) right else null

    fun leftOrDefault(defaultValue: L) = if (isLeft) left else defaultValue
    fun rightOrDefault(defaultValue: R) = if (isRight) right else defaultValue

    fun leftOrElse(producer: () -> L) = if (isLeft) left else producer()
    fun rightOrElse(producer: () -> R) = if (isRight) right else producer()

    private inline class Left(val value: Any?)
    private inline class Right(val value: Any?)

    companion object {
        fun <L, R> left(value: L): Either<L, R> = Either(Left(value))
        fun <L, R> right(value: R): Either<L, R> = Either(Right(value))
    }
}
