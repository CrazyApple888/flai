package me.drew.flai.cli

enum class OutputFormat { TEXT, JSON }

data class RunOptions(
    val pipelineFile: String,
    val inputs: Map<String, String>,
    val inputsJsonFile: String?,
    val workdir: String?,
    val format: OutputFormat,
    val quiet: Boolean,
)

sealed class ParseResult {
    data class Run(val options: RunOptions) : ParseResult()
    data class Error(val message: String) : ParseResult()
}

object ArgParser {

    val usage: String = """
        Usage: flai-cli run <pipeline-file> [options]

        Options:
          --input key=value     Pipeline input; repeatable, wins over --inputs-json
          --inputs-json <file>  JSON object file with pipeline inputs
          --workdir <dir>       Working directory for bash/file gates (default: pipeline project root)
          --format text|json    Final outputs format on stdout (default: text)
          --quiet               Suppress execution event log on stderr

        Credentials: env var FLAI_CREDENTIAL_<ID> (id uppercased, non-alphanumeric -> '_')
        Exit codes: 0 success, 1 pipeline failure, 2 usage error
    """.trimIndent()

    fun parse(args: List<String>): ParseResult {
        if (args.isEmpty()) {
            return ParseResult.Error("Missing command, expected: run <pipeline-file>")
        }
        if (args[0] != "run") {
            return ParseResult.Error("Unknown command '${args[0]}', expected: run")
        }
        var pipelineFile: String? = null
        val inputs = linkedMapOf<String, String>()
        var inputsJsonFile: String? = null
        var workdir: String? = null
        var format = OutputFormat.TEXT
        var quiet = false
        var i = 1
        while (i < args.size) {
            val arg = args[i]
            when {
                arg == "--input" -> {
                    val pair = args.getOrNull(++i)
                        ?: return ParseResult.Error("--input requires key=value")
                    val eq = pair.indexOf('=')
                    if (eq <= 0) {
                        return ParseResult.Error("Invalid --input '$pair', expected key=value")
                    }
                    inputs[pair.substring(0, eq)] = pair.substring(eq + 1)
                }
                arg == "--inputs-json" -> {
                    inputsJsonFile = args.getOrNull(++i)
                        ?: return ParseResult.Error("--inputs-json requires a file path")
                }
                arg == "--workdir" -> {
                    workdir = args.getOrNull(++i)
                        ?: return ParseResult.Error("--workdir requires a directory")
                }
                arg == "--format" -> {
                    val value = args.getOrNull(++i)
                        ?: return ParseResult.Error("--format requires text|json")
                    format = when (value) {
                        "text" -> OutputFormat.TEXT
                        "json" -> OutputFormat.JSON
                        else -> return ParseResult.Error("Unknown format '$value', expected text|json")
                    }
                }
                arg == "--quiet" -> {
                    quiet = true
                }
                arg.startsWith("--") -> {
                    return ParseResult.Error("Unknown option '$arg'")
                }
                pipelineFile == null -> {
                    pipelineFile = arg
                }
                else -> {
                    return ParseResult.Error("Unexpected argument '$arg'")
                }
            }
            i++
        }
        val file = pipelineFile
            ?: return ParseResult.Error("Missing pipeline file")
        return ParseResult.Run(RunOptions(file, inputs, inputsJsonFile, workdir, format, quiet))
    }
}
