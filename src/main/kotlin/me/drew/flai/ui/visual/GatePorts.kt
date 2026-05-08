package me.drew.flai.ui.visual

import me.drew.flai.domain.model.*

fun Gate.inputPorts(): List<String> = when (this) {
    is InputGate -> emptyList()
    else -> listOf("in")
}

fun Gate.outputPorts(): List<String> = when (this) {
    is InputGate -> listOf("out")
    is LogicGate -> branches.map { it.port } + listOfNotNull(defaultPort)
    else -> listOf("out")
}
