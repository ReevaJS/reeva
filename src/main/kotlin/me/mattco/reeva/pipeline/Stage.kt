package me.mattco.reeva.pipeline

import me.mattco.reeva.core.Agent
import me.mattco.reeva.utils.Result
import me.mattco.reeva.utils.flatten

interface Stage<in Input, Output, Error> {
    fun process(agent: Agent, input: Input): Result<Error, Output>

    fun <T> mapValue(mapper: (Output) -> T): Stage<Input, T, Error> {
        val outer = this

        return object : Stage<Input, T, Error> {
            override fun process(agent: Agent, input: Input): Result<Error, T> {
                return outer.process(agent, input).mapValue(mapper)
            }
        }
    }

    fun <T> mapError(mapper: (Error) -> T): Stage<Input, Output, T> {
        val outer = this

        return object : Stage<Input, Output, T> {
            override fun process(agent: Agent, input: Input): Result<T, Output> {
                return outer.process(agent, input).mapError(mapper)
            }
        }
    }

    fun <T> chain(stage: Stage<Output, T, Error>) = Chained(this, stage)

    class Chained<in From, Middle, To, Error>(
        val first: Stage<From, Middle, Error>,
        val second: Stage<Middle, To, Error>
    ) : Stage<From, To, Error> {
        override fun process(agent: Agent, input: From): Result<Error, To> {
            return first.process(agent, input).mapValue {
                second.process(agent, it)
            }.flatten()
        }
    }
}
