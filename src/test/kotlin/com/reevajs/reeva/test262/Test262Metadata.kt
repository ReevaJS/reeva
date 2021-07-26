package com.reevajs.reeva.test262

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Test262Metadata(
    val negative: Negative? = null,
    val includes: List<String>? = null,
    val flags: List<Flag>? = null,
    val locale: List<String>? = null,
    val esid: String? = null,
    val es5id: String? = null,
    val es6id: String? = null,
    val description: String? = null,
    val info: String? = null,
    val features: List<String>? = null,
    val author: String? = null,
)

@Serializable
enum class Flag {
    @SerialName("onlyStrict")
    OnlyStrict,
    @SerialName("noStrict")
    NoStrict,
    @SerialName("module")
    Module,
    @SerialName("raw")
    Raw,
    @SerialName("async")
    Async,
    @SerialName("generated")
    Generated,
    @SerialName("CanBlockIsFalse")
    CanBlockIsFalse,
    @SerialName("CanBlockIsTrue")
    CanBlockIsTrue,
    @SerialName("non-deterministic")
    NonDeterministic,
}

@Serializable
data class Negative(
    val phase: Phase,
    val type: String,
    val flags: Flag? = null,
) {
    @Serializable
    enum class Phase {
        @SerialName("parse")
        Parse,
        @SerialName("early")
        Early,
        @SerialName("resolution")
        Resolution,
        @SerialName("runtime")
        Runtime,
    }

    @Serializable
    enum class Flag {
        @SerialName("module")
        Module,
    }
}
