package me.drew.flai.domain.model

sealed class GateResult {
    data class Success(val outputs: Map<String, Any?> = emptyMap()) : GateResult()
    data class Routed(val port: String, val outputs: Map<String, Any?> = emptyMap()) : GateResult()
    data class Failure(
        val error: Throwable,
        val retryable: Boolean = false,
        val message: String = error.message ?: "Unknown error",
    ) : GateResult()
}
