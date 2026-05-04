package me.drew.flai.infrastructure.executor

import me.drew.flai.domain.executor.GateExecutor
import me.drew.flai.domain.model.*

class DefaultLogicGateExecutor : GateExecutor<LogicGate> {
    override fun canHandle(gate: Gate) = gate is LogicGate

    override suspend fun execute(gate: LogicGate, context: ExecutionContext): GateResult {
        val vars = context.snapshot()
        val matched = gate.branches.firstOrNull { evaluateCondition(it.condition, vars) }
        return when {
            matched != null -> GateResult.Routed(port = matched.port)
            gate.defaultPort != null -> GateResult.Routed(port = gate.defaultPort)
            else -> GateResult.Failure(
                IllegalStateException("No branch matched and no defaultPort for gate ${gate.id.value}")
            )
        }
    }

    private fun evaluateCondition(condition: BranchCondition, vars: Map<String, Any?>): Boolean =
        when (condition) {
            is BranchCondition.Always -> true
            is BranchCondition.SwitchCase ->
                vars[condition.variable]?.toString() in condition.values
            is BranchCondition.Comparison -> {
                val lhs = vars[condition.variable]?.toString() ?: ""
                val rhs = condition.value
                when (condition.op) {
                    ComparisonOp.EQ -> lhs == rhs
                    ComparisonOp.NEQ -> lhs != rhs
                    ComparisonOp.CONTAINS -> lhs.contains(rhs)
                    ComparisonOp.STARTS_WITH -> lhs.startsWith(rhs)
                    ComparisonOp.GT -> lhs.toDoubleOrNull()?.let { it > (rhs.toDoubleOrNull() ?: 0.0) } ?: false
                    ComparisonOp.GTE -> lhs.toDoubleOrNull()?.let { it >= (rhs.toDoubleOrNull() ?: 0.0) } ?: false
                    ComparisonOp.LT -> lhs.toDoubleOrNull()?.let { it < (rhs.toDoubleOrNull() ?: 0.0) } ?: false
                    ComparisonOp.LTE -> lhs.toDoubleOrNull()?.let { it <= (rhs.toDoubleOrNull() ?: 0.0) } ?: false
                }
            }
        }
}
