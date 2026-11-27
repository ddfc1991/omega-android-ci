/*
 * Adapted from team-dynamics-plugin (Hassn / 米伴), MIT license.
 * Package renamed for Mibot; logic unchanged.
 */
// DynamicsPlugin.kt
// 动态 feed 的安卓插件入口：把 TeamDynamics.kt（同一目录下的
// com.hassn.app.ui.dynamics 包，原文件一字未改）包成一个可插拔模块。
//
// 宿主（Hassn / 米伴）只用三行接进来：
//   val vm = DynamicsPlugin.createViewModel()
//   DynamicsPlugin.Screen(vm) { activity -> /* 点卡片：有 goalId 就进目标页 */ }
//   DynamicsPlugin.pushJson(vm, json)   // mines / DSH 那边推来的事件直接喂进来
//
// 事件 JSON 和 DSH 插件 dsh-plugin-team-dynamics 完全一致，两边互通：
// {"id":"evt_001","agentName":"mines","type":"FINDING","title":"发现越权接口",
//  "summary":"用普通账号拿到了管理员数据，已交 triage","timestampMillis":1794100000000,
//  "goalId":"goal_bugbounty","goalTitle":"漏洞盒子挖洞"}
//
// 新增依赖只有一个：org.json（Android 自带，不用加库）。
// 界面、分组、防重复、状态行语义全部复用 TeamDynamics.kt，不在这里重写第二份。

package com.openminis.app.mibot.dynamics

import androidx.compose.runtime.Composable
import com.openminis.app.mibot.dynamics.ActivityType
import com.openminis.app.mibot.dynamics.AgentActivity
import com.openminis.app.mibot.dynamics.DynamicsViewModel
import com.openminis.app.mibot.dynamics.TeamDynamicsScreen
import org.json.JSONArray
import org.json.JSONObject

object DynamicsPlugin {

    const val PLUGIN_ID = "team-dynamics"
    const val VERSION = "0.1.0"

    /** 宿主建一个 ViewModel 持有动态流；一页动态用一个实例。 */
    fun createViewModel(): DynamicsViewModel = DynamicsViewModel()

    /** 动态页本身。点卡片时把事件原样交回宿主，宿主按 goalId 决定跳不跳目标页。 */
    @Composable
    fun Screen(
        viewModel: DynamicsViewModel,
        onActivityClick: (AgentActivity) -> Unit = {},
    ) {
        TeamDynamicsScreen(viewModel = viewModel, onActivityClick = onActivityClick)
    }

    sealed interface PushResult {
        data class Ok(val event: AgentActivity) : PushResult
        data class Error(val message: String) : PushResult
    }

    /** 喂一条事件 JSON 进来。成功进流返回 Ok，格式不对返回 Error，不抛异常。 */
    fun pushJson(viewModel: DynamicsViewModel, json: String): PushResult {
        val parsed = parseEvent(json)
        if (parsed is PushResult.Error) return parsed
        val event = (parsed as PushResult.Ok).event
        viewModel.push(event)
        return parsed
    }

    /** 一次喂一批事件 JSON 数组。返回成功进流的条数，坏条跳过不中断。 */
    fun pushAllJson(viewModel: DynamicsViewModel, jsonArray: String): Int {
        val count: Int
        try {
            val arr = JSONArray(jsonArray)
            var ok = 0
            for (i in 0 until arr.length()) {
                val item = arr.opt(i) ?: continue
                val one = pushJson(viewModel, item.toString())
                if (one is PushResult.Ok) ok++
            }
            count = ok
        } catch (_: Exception) {
            return 0
        }
        return count
    }

    /** 把当前流里的全部事件导成 JSON 数组（和 DSH 插件同一格式，方便两边对账）。 */
    fun exportJson(viewModel: DynamicsViewModel): String {
        val arr = JSONArray()
        // groups 是按天分好组的流，这里把它拍平成一条事件数组。
        // 同一 id 只会出现一次（push 时已去重）。
        val seen = HashSet<String>()
        for (group in viewModel.groups.value) {
            for (a in group.items) {
                if (!seen.add(a.id)) continue
                arr.put(toJson(a))
            }
        }
        return arr.toString()
    }

    /** 预览 / 自测用的示例事件，和 TeamDynamics.kt 里 sampleActivities 的前两条一致。 */
    fun sampleJson(): String {
        val now = System.currentTimeMillis()
        return JSONArray().apply {
            put(toJson(AgentActivity(
                id = "evt_sample_1", agentName = "mines", type = ActivityType.FINDING,
                title = "发现未授权接口",
                summary = "去掉登录态直接请求拿到了真实数据，已交 triage 判断。",
                timestampMillis = now - 5 * 60 * 1000,
                goalId = "goal_bugbounty", goalTitle = "漏洞盒子挖洞",
            )))
            put(toJson(AgentActivity(
                id = "evt_sample_2", agentName = "Hassn", type = ActivityType.WAITING_APPROVAL,
                title = "报告已写好，等你点头提交",
                summary = "截图已嵌进正文，点这张卡看报告全文。",
                timestampMillis = now - 32 * 60 * 1000,
                goalId = "goal_bugbounty", goalTitle = "漏洞盒子挖洞",
            )))
        }.toString()
    }

    // ---------- 内部：JSON 与 AgentActivity 互转 ----------

    fun parseEvent(json: String): PushResult {
        val obj: JSONObject
        try {
            obj = JSONObject(json)
        } catch (e: Exception) {
            return PushResult.Error("JSON 解析失败：${e.message}")
        }
        val typeName = obj.optString("type", "")
        val type = try {
            ActivityType.valueOf(typeName)
        } catch (_: IllegalArgumentException) {
            return PushResult.Error("type 必须是 ${ActivityType.entries.joinToString("、")} 之一，收到的是 $typeName")
        }
        val title = obj.optString("title", "")
        if (title.isBlank()) return PushResult.Error("title 不能为空")
        return PushResult.Ok(
            AgentActivity(
                id = obj.optString("id", "").ifBlank {
                    "evt_${System.currentTimeMillis()}_${(0..9999).random()}"
                },
                agentName = obj.optString("agentName", "").ifBlank { "Hassn" },
                type = type,
                title = title,
                summary = obj.optString("summary", ""),
                timestampMillis = obj.optLong("timestampMillis", System.currentTimeMillis()),
                goalId = obj.optString("goalId", "").takeIf { it.isNotBlank() },
                goalTitle = obj.optString("goalTitle", "").takeIf { it.isNotBlank() },
            ),
        )
    }

    fun toJson(a: AgentActivity): JSONObject = JSONObject().apply {
        put("id", a.id)
        put("agentName", a.agentName)
        put("type", a.type.name)
        put("title", a.title)
        put("summary", a.summary)
        put("timestampMillis", a.timestampMillis)
        a.goalId?.let { put("goalId", it) }
        a.goalTitle?.let { put("goalTitle", it) }
    }
}
