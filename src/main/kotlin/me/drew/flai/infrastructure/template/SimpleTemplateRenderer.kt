package me.drew.flai.infrastructure.template

import me.drew.flai.domain.port.TemplateRenderer

class SimpleTemplateRenderer : TemplateRenderer {
    private val pattern = Regex("""\{\{(\w+)\}\}""")

    override fun render(template: String, variables: Map<String, Any?>): String =
        pattern.replace(template) { match ->
            variables[match.groupValues[1]]?.toString() ?: match.value
        }
}
