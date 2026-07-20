package wiki.nplus.airadar.digester

import io.micrometer.core.instrument.MeterRegistry
import wiki.nplus.airadar.common.ItemRepository

/**
 * The single write path for LLM spend: the `llm_usage` ledger row and the
 * Prometheus counters, booked together.
 *
 * They used to be separate. The digest path incremented the counters inline;
 * the curator and the essayist only ever wrote the ledger. So Grafana's LLM
 * cost panel showed the cheap per-item tier and nothing else, while the
 * pro-tier SELECT and ESSAY calls — the expensive half of the bill — were
 * invisible on it. A cost spike could not be diagnosed from the dashboard at
 * all; it had to be reconstructed from SQL. Going through here makes recording
 * spend without publishing it impossible, and the purpose/model labels let the
 * panel break the bill down by what the money actually bought.
 */
class UsageMeter(private val repo: ItemRepository, private val registry: MeterRegistry) {
    fun record(itemId: Long?, purpose: String, llm: LlmClient, inputTokens: Int, outputTokens: Int) =
        record(itemId, purpose, llm.model, inputTokens, outputTokens, llm.cost(inputTokens, outputTokens))

    fun record(itemId: Long?, purpose: String, model: String, inputTokens: Int, outputTokens: Int, costUsd: Double) {
        repo.recordUsage(itemId, purpose, model, inputTokens, outputTokens, costUsd)
        counter("airadar_llm_tokens_total", purpose, model, "type", "input").increment(inputTokens.toDouble())
        counter("airadar_llm_tokens_total", purpose, model, "type", "output").increment(outputTokens.toDouble())
        counter("airadar_llm_cost_usd_total", purpose, model).increment(costUsd)
    }

    private fun counter(name: String, purpose: String, model: String, vararg extra: String) =
        registry.counter(name, *extra, "purpose", purpose, "model", model)
}
