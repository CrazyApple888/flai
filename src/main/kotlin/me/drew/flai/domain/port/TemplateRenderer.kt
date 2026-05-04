package me.drew.flai.domain.port

interface TemplateRenderer {
    fun render(template: String, variables: Map<String, Any?>): String
}
