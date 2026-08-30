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
    val inputSchema: ToolInputSchema
    suspend fun invoke(inputs: Map<String, Any?>, context: ExecutionContext): ToolResult
}

data class ToolResult(
    val outputs: Map<String, Any?>,
    val isError: Boolean = false,
)

data class ToolInputSchema(
    val properties: Map<String, ToolSchemaProperty>,
    val required: List<String> = emptyList(),
)

data class ToolSchemaProperty(
    val type: ToolSchemaType,
    val description: String = "",
    val items: ToolSchemaProperty? = null,
)

enum class ToolSchemaType { STRING, NUMBER, INTEGER, BOOLEAN, OBJECT, ARRAY }
