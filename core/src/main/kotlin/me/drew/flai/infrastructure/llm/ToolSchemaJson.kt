package me.drew.flai.infrastructure.llm

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import me.drew.flai.domain.port.ToolInputSchema
import me.drew.flai.domain.port.ToolSchemaProperty

internal fun toolSchemaJson(schema: ToolInputSchema): JsonObject = JsonObject().apply {
    addProperty("type", "object")
    add("properties", JsonObject().also { properties ->
        schema.properties.forEach { (name, property) ->
            properties.add(name, toolPropertyJson(property))
        }
    })
    if (schema.required.isNotEmpty()) {
        add("required", JsonArray().also { required -> schema.required.forEach(required::add) })
    }
}

private fun toolPropertyJson(property: ToolSchemaProperty): JsonObject = JsonObject().apply {
    addProperty("type", property.type.name.lowercase())
    if (property.description.isNotBlank()) {
        addProperty("description", property.description)
    }
    property.items?.let { add("items", toolPropertyJson(it)) }
}
