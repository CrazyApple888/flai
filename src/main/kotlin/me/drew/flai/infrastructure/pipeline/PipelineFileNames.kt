package me.drew.flai.infrastructure.pipeline

private fun isFlaiPipelineFileName(name: String): Boolean =
    name.endsWith(".flai.yaml") || name.endsWith(".flai.yml") || name.endsWith(".flai")

internal fun isPipelineFileName(name: String): Boolean =
    isFlaiPipelineFileName(name) || name.endsWith(".yaml") || name.endsWith(".yml")
