package me.drew.flai.cli

import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    when (val result = ArgParser.parse(args.toList())) {
        is ParseResult.Error -> {
            System.err.println(result.message)
            System.err.println(ArgParser.usage)
            exitProcess(2)
        }
        is ParseResult.Run -> {
            val exitCode = runBlocking {
                CliRunner(System.out, System.err).run(result.options)
            }
            exitProcess(exitCode)
        }
    }
}
