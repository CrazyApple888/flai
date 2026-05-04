package me.drew.flai.domain.port

import me.drew.flai.domain.model.ExecutionContext

interface ToolRegistry {
    fun register(tool: Tool)
    fun get(name: String): Tool?
    fun listNames(): List<String>
}

interface Tool {
    val name: String
    val description: String
    suspend fun invoke(inputs: Map<String, Any?>, context: ExecutionContext): Map<String, Any?>
}
