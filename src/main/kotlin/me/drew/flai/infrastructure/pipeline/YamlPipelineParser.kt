package me.drew.flai.infrastructure.pipeline

import me.drew.flai.domain.model.*
import me.drew.flai.domain.port.PipelineLoadException
import org.yaml.snakeyaml.Yaml

class YamlPipelineParser {
    private val yaml = Yaml()

    @Suppress("UNCHECKED_CAST")
    fun parse(content: String): Pipeline {
        val root = yaml.load<Map<String, Any>>(content)
            ?: throw PipelineLoadException("Empty pipeline file")

        val id = PipelineId(root["id"] as? String ?: throw PipelineLoadException("Missing 'id'"))
        val name = root["name"] as? String ?: throw PipelineLoadException("Missing 'name'")
        val description = root["description"] as? String ?: ""
        val entryKey = root["entry"] as? String ?: throw PipelineLoadException("Missing 'entry'")

        val gatesRaw = root["gates"] as? Map<String, Any>
            ?: throw PipelineLoadException("Missing 'gates'")

        val gates = gatesRaw.entries.associate { (key, value) ->
            val gateId = GateId(key)
            val gateMap = value as? Map<String, Any>
                ?: throw PipelineLoadException("Gate '$key' must be a map")
            gateId to parseGate(gateId, gateMap)
        }

        val edgesRaw = root["edges"] as? List<Any> ?: emptyList<Any>()
        val edges = edgesRaw.map { edgeObj ->
            val e = edgeObj as? Map<String, Any>
                ?: throw PipelineLoadException("Edge must be a map")
            PipelineEdge(
                from = GateId(e["from"] as? String ?: throw PipelineLoadException("Edge missing 'from'")),
                fromPort = e["fromPort"] as? String ?: "out",
                to = GateId(e["to"] as? String ?: throw PipelineLoadException("Edge missing 'to'")),
                toPort = e["toPort"] as? String ?: "in",
            )
        }

        return Pipeline(
            id = id,
            name = name,
            description = description,
            gates = gates,
            edges = edges,
            entryGateId = GateId(entryKey),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseGate(id: GateId, map: Map<String, Any>): Gate {
        val type = map["type"] as? String ?: throw PipelineLoadException("Gate '${id.value}' missing 'type'")
        val label = map["label"] as? String ?: id.value

        if (type != "llm" && map.containsKey("skills")) {
            throw PipelineLoadException(
                "Gate '${id.value}' of type '$type' does not support 'skills'"
            )
        }

        return when (type) {
            "input" -> InputGate(
                id = id,
                label = label,
                inputSchema = parseInputSchema(map["schema"]),
            )
            "output" -> OutputGate(
                id = id,
                label = label,
                outputMapping = parseStringMap(map["outputMapping"]),
            )
            "llm" -> LlmGate(
                id = id,
                label = label,
                promptTemplate = map["promptTemplate"] as? String ?: "",
                skills = parseSkillsList(id, map["skills"]),
                inputMapping = parseStringMap(map["inputMapping"]),
                outputMapping = parseStringMap(map["outputMapping"]).ifEmpty { mapOf("response" to "response") },
                endpointConfig = parseEndpointConfig(id, map["endpoint"]),
            )
            "logic" -> LogicGate(
                id = id,
                label = label,
                branches = parseBranches(id, map["branches"]),
                defaultPort = map["defaultPort"] as? String ?: "default",
            )
            "tool" -> ToolGate(
                id = id,
                label = label,
                toolName = map["tool"] as? String
                    ?: throw PipelineLoadException("Tool gate '${id.value}' missing 'tool'"),
                inputMapping = parseStringMap(map["inputMapping"]),
                outputMapping = parseStringMap(map["outputMapping"]),
            )
            "read-file" -> ReadFileGate(
                id = id,
                label = label,
                path = map["path"] as? String
                    ?: throw PipelineLoadException("read-file gate '${id.value}' missing 'path'"),
                outputKey = map["outputKey"] as? String ?: "content",
            )
            "write-file" -> WriteFileGate(
                id = id,
                label = label,
                path = map["path"] as? String
                    ?: throw PipelineLoadException("write-file gate '${id.value}' missing 'path'"),
                contentKey = map["contentKey"] as? String
                    ?: throw PipelineLoadException("write-file gate '${id.value}' missing 'contentKey'"),
                mode = when (val m = map["mode"] as? String ?: "overwrite") {
                    "overwrite" -> WriteMode.OVERWRITE
                    "append" -> WriteMode.APPEND
                    "fail-if-exists" -> WriteMode.FAIL_IF_EXISTS
                    else -> throw PipelineLoadException("write-file gate '${id.value}': unknown mode '$m'")
                },
            )
            else -> throw PipelineLoadException("Unknown gate type '$type' for gate '${id.value}'")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseInputSchema(obj: Any?): List<InputField> {
        val list = obj as? List<Any> ?: return emptyList()
        return list.map { item ->
            val m = item as? Map<String, Any> ?: return@map InputField("unknown", FieldType.STRING)
            InputField(
                name = m["name"] as? String ?: "unknown",
                type = runCatching { FieldType.valueOf((m["type"] as? String ?: "STRING").uppercase()) }
                    .getOrDefault(FieldType.STRING),
                required = m["required"] as? Boolean ?: true,
                default = m["default"] as? String,
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseStringMap(obj: Any?): Map<String, String> {
        val map = obj as? Map<*, *> ?: return emptyMap()
        return map.entries
            .filter { it.key != null && it.value != null }
            .associate { it.key.toString() to it.value.toString() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseEndpointConfig(gateId: GateId, obj: Any?): LlmEndpointConfig {
        val m = obj as? Map<String, Any>
            ?: throw PipelineLoadException("LLM gate '${gateId.value}' missing 'endpoint'")
        val params = (m["params"] as? Map<String, Any>)?.toMap() ?: emptyMap()
        return LlmEndpointConfig(
            url = m["url"] as? String ?: throw PipelineLoadException("Endpoint missing 'url'"),
            credentialId = m["credentialId"] as? String
                ?: throw PipelineLoadException("Endpoint missing 'credentialId'"),
            model = m["model"] as? String ?: throw PipelineLoadException("Endpoint missing 'model'"),
            params = params,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseBranches(gateId: GateId, obj: Any?): List<Branch> {
        if (obj == null) return emptyList()
        val list = obj as? List<Any>
            ?: throw PipelineLoadException("Logic gate '${gateId.value}': 'branches' must be a list")
        return list.map { item ->
            val m = item as? Map<String, Any>
                ?: throw PipelineLoadException("Branch must be a map")
            val port = m["port"] as? String ?: throw PipelineLoadException("Branch missing 'port'")
            val condition = parseCondition(gateId, m["condition"])
            Branch(port, condition)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSkillsList(gateId: GateId, obj: Any?): List<String> {
        if (obj == null) {
            return emptyList()
        }
        val list = obj as? List<*>
            ?: throw PipelineLoadException(
                "Gate '${gateId.value}': 'skills' must be a list of file paths, got ${obj::class.simpleName}"
            )
        return list.mapIndexed { i, item ->
            item as? String
                ?: throw PipelineLoadException(
                    "Gate '${gateId.value}': 'skills[$i]' must be a string, got ${item?.let { it::class.simpleName } ?: "null"}"
                )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseCondition(gateId: GateId, obj: Any?): BranchCondition {
        val m = obj as? Map<String, Any> ?: return BranchCondition.Always
        return when (val condType = m["type"] as? String) {
            "always" -> BranchCondition.Always
            "switch" -> BranchCondition.SwitchCase(
                variable = m["variable"] as? String ?: throw PipelineLoadException("SwitchCase missing 'variable'"),
                values = (m["values"] as? List<Any>)?.map { it.toString() } ?: emptyList(),
            )
            "comparison", null -> BranchCondition.Comparison(
                variable = m["variable"] as? String ?: throw PipelineLoadException("Comparison missing 'variable'"),
                op = runCatching { ComparisonOp.valueOf((m["op"] as? String ?: "EQ").uppercase()) }
                    .getOrDefault(ComparisonOp.EQ),
                value = m["value"]?.toString() ?: "",
            )
            else -> throw PipelineLoadException("Unknown condition type '$condType'")
        }
    }
}
