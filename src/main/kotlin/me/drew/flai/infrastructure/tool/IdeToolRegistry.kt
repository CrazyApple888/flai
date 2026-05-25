package me.drew.flai.infrastructure.tool

import me.drew.flai.domain.port.Tool
import me.drew.flai.domain.port.ToolRegistry
import java.util.concurrent.ConcurrentHashMap

class IdeToolRegistry : ToolRegistry {
    private val tools = ConcurrentHashMap<String, Tool>()

    override fun register(tool: Tool) {
        tools[tool.name] = tool
    }
    override fun get(name: String): Tool? = tools[name]
    override fun listNames(): List<String> = tools.keys.sorted()
}
