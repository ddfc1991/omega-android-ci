package com.openminis.app.mibot.gateway

import android.content.Context
import com.openminis.app.data.model.ModelEntry
import com.openminis.app.data.model.ProviderInstance
import com.openminis.app.data.repository.ProviderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * [fork/magpie-gateway] Health checks for the local Magpie gateway
 * (http://127.0.0.1:3425/v1, OpenAI-compatible).
 *
 * Magpie fronts a rotating pool of free upstream accounts (Runable / Loomy /
 * LongCat / Trae-CN / ...). Two jobs, both automatic:
 *
 *  1. MODEL LIST REFRESH — Magpie serves its live account pool on /v1/models.
 *     A provider instance pointed at the gateway refreshes through the normal
 *     ProviderRepository.refreshModels() vendor path; this module schedules
 *     that refresh once a day so new upstream models appear without the user
 *     tapping anything.
 *
 *  2. LATENCY PROBE — each model gets a minimal chat request ("hi",
 *     max_tokens=1). Models that answer sort FIRST (fastest first); models
 *     that fail sort LAST; unprobed models keep the default release-rank
 *     order in between. The snapshot is a JSON file in filesDir so the picker
 *     (a pure function, no suspend) can order without a repo or a network.
 *
 * Results live in filesDir/mibot/magpie_health.json:
 *   { "version": 1, "lastRefresh": ms, "lastCheck": ms,
 *     "models": [ {"id": "loomy/auto", "ok": true, "ms": 890}, ... ] }
 */
object MagpieHealth {
    private const val TAG = "MagpieHealth"
    const val GATEWAY_PORT = 3425
    const val BASE_URL = "http://127.0.0.1:$GATEWAY_PORT/v1"

    /** Re-probe when the snapshot is older than this (24h). */
    private const val STALE_MS = 24L * 60 * 60 * 1000
    private const val PROBE_CONCURRENCY = 6

    /** True when this instance points at the local Magpie gateway. */
    fun isMagpie(instance: ProviderInstance): Boolean {
        val base = instance.customBaseURL ?: return false
        return base.contains("127.0.0.1:$GATEWAY_PORT") || base.contains("localhost:$GATEWAY_PORT")
    }

    fun isMagpie(base: String?): Boolean =
        base != null && (base.contains("127.0.0.1:$GATEWAY_PORT") || base.contains("localhost:$GATEWAY_PORT"))

    // ─── snapshot ─────────────────────────────────────────────────────────

    data class Probe(val id: String, val ok: Boolean, val ms: Long)
    data class Snapshot(
        val lastRefresh: Long,
        val lastCheck: Long,
        val probes: Map<String, Probe>,
    ) {
        val hasData: Boolean get() = probes.isNotEmpty()
    }

    private fun file(ctx: Context): File =
        File(ctx.applicationContext.filesDir, "mibot/magpie_health.json")

    @Volatile private var cached: Snapshot? = null

    fun snapshot(ctx: Context): Snapshot? {
        cached?.let { return it }
        val s = runCatching {
            val o = JSONObject(file(ctx).readText())
            val arr = o.optJSONArray("models") ?: JSONArray()
            val probes = LinkedHashMap<String, Probe>(arr.length())
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val p = Probe(m.optString("id"), m.optBoolean("ok"), m.optLong("ms", -1))
                probes[p.id] = p
            }
            Snapshot(o.optLong("lastRefresh"), o.optLong("lastCheck"), probes)
        }.getOrNull()
        if (s != null) cached = s
        return s
    }

    /** Caller is already on IO (refreshAndCheck) or the main scope's launch(IO). */
    private fun save(ctx: Context, s: Snapshot) {
        cached = s
        runCatching {
            val o = JSONObject()
                .put("version", 1)
                .put("lastRefresh", s.lastRefresh)
                .put("lastCheck", s.lastCheck)
            val arr = JSONArray()
            s.probes.values.forEach { arr.put(JSONObject().put("id", it.id).put("ok", it.ok).put("ms", it.ms)) }
            o.put("models", arr)
            file(ctx).parentFile?.mkdirs()
            file(ctx).writeText(o.toString(2))
        }
    }

    // ─── ordering ─────────────────────────────────────────────────────────

    /**
     * Sort entries for a Magpie section: alive first (fastest first), never
     * probed in the middle in the caller's default order, dead last. Pure and
     * total — the fallback comparator keeps release-rank order and then the
     * model id so the list never reshuffles between reads.
     */
    fun sortEntries(ctx: Context, entries: List<ModelEntry>): List<ModelEntry> {
        if (entries.size < 2) return entries
        val snap = snapshot(ctx) ?: return entries
        return sortEntries(snap, entries)
    }

    /**
     * Pure variant for callers that already hold a snapshot (the picker reads
     * it once per composition and passes it down; reading a file inside the
     * per-section loop would thrash).
     */
    fun sortEntries(snap: Snapshot, entries: List<ModelEntry>): List<ModelEntry> {
        if (entries.size < 2 || !snap.hasData) return entries
        val fallback = com.openminis.app.data.repository.ModelEntryRanking
            .sortedByReleaseRank(entries)
        return fallback.sortedWith(
            compareBy(
                { rankOf(snap.probes[it.baseModel.id]) },
                { snap.probes[it.baseModel.id]?.ms ?: Long.MAX_VALUE },
            )
        )
    }

    /** 0 = alive (best), 1 = unknown, 2 = dead (worst). */
    private fun rankOf(p: Probe?): Int = when {
        p == null -> 1
        p.ok -> 0
        else -> 2
    }

    // ─── refresh + probe ───────────────────────────────────────────────────

    /**
     * One full pass: pull Magpie's live model list into the provider instance
     * (vendor path), then probe every entry concurrently. Called from the
     * daily scheduler and from the gateway health screen's "体检" row.
     */
    suspend fun refreshAndCheck(
        ctx: Context,
        repo: ProviderRepository,
        instance: ProviderInstance,
    ): Snapshot = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        // 1) Live model list. refreshModels() is the same vendor path a manual
        //    pull uses; failure just keeps yesterday's entries.
        runCatching { repo.refreshModels(instance, forceRefresh = true) }
            .onFailure { android.util.Log.w(TAG, "model refresh failed: ${it.message}") }

        // 2) Probe every entry the instance now lists.
        val ids = repo.entriesFor(instance.id).map { it.baseModel.id }.distinct()
        val semaphore = Semaphore(PROBE_CONCURRENCY)
        val probes = coroutineScope {
            ids.map { id ->
                async {
                    semaphore.withPermit {
                        val o = GatewayNet.request(
                            instanceId = instance.id,
                            url = "$BASE_URL/chat/completions",
                            method = "POST",
                            headers = mapOf("Content-Type" to "application/json"),
                            jsonBody = JSONObject()
                                .put("model", id)
                                .put(
                                    "messages",
                                    JSONArray().put(JSONObject().put("role", "user").put("content", "hi")),
                                )
                                .put("max_tokens", 1)
                                .toString(),
                        )
                        // kind OK → the round trip completed with an HTTP-level
                        // answer; some upstreams "answer" with 4xx (quota/auth on
                        // one pooled account) which still proves the pool's
                        // routing works — count only 2xx-with-choices as alive.
                        val ok = o.kind == GatewayNet.Kind.OK && !o.body.isBlank() &&
                            runCatching {
                                JSONObject(o.body).optJSONArray("choices")?.length() ?: 0 > 0
                            }.getOrDefault(false)
                        Probe(id, ok, o.latencyMs)
                    }
                }
            }.awaitAll()
        }

        val byId = LinkedHashMap<String, Probe>(probes.size)
        probes.forEach { byId[it.id] = it }
        Snapshot(lastRefresh = now, lastCheck = now, probes = byId).also { save(ctx, it) }
    }

    /**
     * Daily gate for app start: refresh + probe only when the snapshot is
     * stale AND a Magpie instance exists AND the gateway port answers.
     * Otherwise a cold start with no Magpie installed burns a 20s timeout per
     * model for nothing. Fire-and-forget on IO.
     */
    fun maybeAutoRefresh(ctx: Context, repo: ProviderRepository, scope: kotlinx.coroutines.CoroutineScope) {
        val app = ctx.applicationContext
        val stale = (snapshot(app)?.lastRefresh ?: 0) < System.currentTimeMillis() - STALE_MS
        if (!stale) return
        scope.launch(Dispatchers.IO) {
            val instance = runCatching { repo.instances.firstOrNull { isMagpie(it) } }.getOrNull() ?: return@launch
            // Cheap gate first: a 3s TCP-level check that the gateway is up.
            val up = runCatching {
                java.net.Socket().use { s ->
                    s.connect(java.net.InetSocketAddress("127.0.0.1", GATEWAY_PORT), 3000)
                }
                true
            }.getOrDefault(false)
            if (!up) {
                android.util.Log.i(TAG, "gateway down, skipping daily probe")
                return@launch
            }
            runCatching { refreshAndCheck(app, repo, instance) }
                .onFailure { android.util.Log.w(TAG, "daily probe failed: ${it.message}") }
        }
    }
}
