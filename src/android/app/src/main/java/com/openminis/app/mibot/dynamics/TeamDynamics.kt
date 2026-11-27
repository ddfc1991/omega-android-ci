/*
 * Adapted from team-dynamics-plugin (Hassn / 米伴), MIT license.
 * Package renamed for Mibot; logic unchanged.
 */
// TeamDynamics.kt
// Hassn 团队动态功能 —— 干净实现版（不是 Muse 的内部源码，Muse 内部代码不在我手里、也不能外发）
// 版式按主人 2026-10-25 定的那页：
//   顶部：头像 + 名字；下面一行：当前状态（它此刻正在干什么）
//   中部：按时间分组的动态流（今天 / 昨天 / 日期），一张卡一条进展
//   每张卡：谁做的、标题、摘要、时间、类型、挂在哪个目标下（可点进目标页追溯）
// 无障碍：整页按时间顺序朗读；状态点不靠颜色单独传意，卡片带完整 contentDescription。
//
// 后端 / mines 推事件的 JSON 格式（一行一条，和 AgentActivity 字段一一对应）：
// {"id":"evt_001","agentName":"mines","type":"FINDING","title":"发现越权接口",
//  "summary":"用普通账号拿到了管理员数据，已交 triage","timestampMillis":1794100000000,
//  "goalId":"goal_bugbounty","goalTitle":"漏洞盒子挖洞"}
//
// 用法：把本文件放进 com.hassn.app.ui.dynamics 包，
// 在导航里加一页 TeamDynamicsScreen(viewModel) 即可；皮（颜色/圆角/字体）
// 在 ActivityCard 和 DynamicsHeader 里改成 Hark 那套主题，本文件不写死品牌色。

package com.openminis.app.mibot.dynamics

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------- 1. 数据模型 ----------

enum class ActivityType(val label: String) {
    TASK_STARTED("开始任务"),
    TOOL_USED("用了工具"),
    FINDING("有发现"),
    HANDOFF("已交付"),
    WAITING_APPROVAL("等你点头"),
    DONE("已完成"),
    FAILED("出了问题"),
}

data class AgentActivity(
    val id: String,                 // 唯一 id，后端给；重复 id 只保留一条（防重复推送）
    val agentName: String,          // 谁做的：hassn / mines / cue / hark …
    val type: ActivityType,
    val title: String,              // 一句话标题，朗读时先读它
    val summary: String,            // 摘要：做了什么、拿到什么、下一步什么
    val timestampMillis: Long,      // 事件发生时间（不是推送到达时间）
    val goalId: String? = null,     // 挂在哪个目标下，点卡片跳目标页追溯全程
    val goalTitle: String? = null,
)

data class AgentStatus(
    val agentName: String,          // 顶部显示的名字，如 "Hassn"
    val avatarUrl: String? = null,  // 头像地址；为空时用名字首字占位
    val currentAction: String,      // 当前状态一行字，如 "正在复现 ilab 未授权接口"
    val isWorking: Boolean = true,  // true 时状态点呼吸闪烁；false 显示静止
)

data class DayGroup(
    val label: String,              // 今天 / 昨天 / 11月6日 星期四
    val date: LocalDate,
    val items: List<AgentActivity>, // 组内按时间倒序（新的在上）
)

// ---------- 2. 分组逻辑（纯函数，可单独测） ----------

fun groupActivitiesByDay(
    items: List<AgentActivity>,
    zoneId: ZoneId = ZoneId.of("Asia/Shanghai"),
    today: LocalDate = LocalDate.now(zoneId),
): List<DayGroup> {
    val dateFmt = DateTimeFormatter.ofPattern("M月d日 EEEE")
    return items
        .distinctBy { it.id }                              // 防重复
        .sortedByDescending { it.timestampMillis }         // 先总排序
        .groupBy { Instant.ofEpochMilli(it.timestampMillis).atZone(zoneId).toLocalDate() }
        .toSortedMap(compareByDescending { it })           // 日期新的在前
        .map { (date, dayItems) ->
            DayGroup(
                label = when (date) {
                    today -> "今天"
                    today.minusDays(1) -> "昨天"
                    else -> date.format(dateFmt)
                },
                date = date,
                items = dayItems,
            )
        }
}

private fun formatTime(timestampMillis: Long, zoneId: ZoneId): String =
    Instant.ofEpochMilli(timestampMillis)
        .atZone(zoneId)
        .format(DateTimeFormatter.ofPattern("HH:mm"))

// ---------- 3. ViewModel：状态 + 动态流，谁推事件都走这里 ----------

class DynamicsViewModel : ViewModel() {

    private val _status = MutableStateFlow(
        AgentStatus(agentName = "Hassn", currentAction = "待命中", isWorking = false)
    )
    val status: StateFlow<AgentStatus> = _status.asStateFlow()

    private val _activities = MutableStateFlow<List<AgentActivity>>(emptyList())
    /** 已按天分好组、倒序的动态流，UI 直接渲染这一份。 */
    val groups: StateFlow<List<DayGroup>> =
        MutableStateFlow<List<DayGroup>>(emptyList()).also { out ->
            viewModelScope.launch {
                _activities.collect { out.value = groupActivitiesByDay(it) }
            }
        }

    /** 顶部状态一行字：某个智能体开始/切换动作时调它。 */
    fun setCurrentAction(agentName: String, action: String, working: Boolean = true) {
        _status.update { it.copy(agentName = agentName, currentAction = action, isWorking = working) }
    }

    /** 推一条新动态。重复 id 自动忽略；顺手把顶部状态更新成这条的标题。 */
    fun push(activity: AgentActivity) {
        _activities.update { old ->
            if (old.any { it.id == activity.id }) old else old + activity
        }
        _status.update {
            it.copy(
                agentName = activity.agentName,
                currentAction = activity.title,
                isWorking = activity.type != ActivityType.DONE &&
                    activity.type != ActivityType.FAILED,
            )
        }
    }

    fun replaceAll(items: List<AgentActivity>) {
        _activities.value = items.distinctBy { it.id }
    }
}

// ---------- 4. UI：整页 ----------

@Composable
fun TeamDynamicsScreen(
    viewModel: DynamicsViewModel,
    onActivityClick: (AgentActivity) -> Unit = {},   // 点卡片：有 goalId 就进目标页追溯
    zoneId: ZoneId = ZoneId.of("Asia/Shanghai"),
) {
    val status by viewModel.status.collectAsState()
    val groups by viewModel.groups.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        DynamicsHeader(status = status)

        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "还没有动态。有新进展会自动出现在这里。",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { contentDescription = "暂无动态" },
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            groups.forEach { group ->
                item(key = "day_${group.date}") { DayHeader(group.label) }
                items(group.items, key = { it.id }) { activity ->
                    ActivityCard(activity, zoneId) { onActivityClick(activity) }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DynamicsHeader(status: AgentStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .semantics {
                contentDescription =
                    "${status.agentName}，当前状态：${status.currentAction}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 头像：有图时在这里换成 AsyncImage；占位用名字首字，不写死任何形象。
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                status.agentName.firstOrNull()?.toString() ?: "?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(status.agentName, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(working = status.isWorking)
                Spacer(Modifier.width(6.dp))
                Text(status.currentAction, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatusDot(working: Boolean) {
    if (!working) {
        Box(Modifier.size(8.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.outline))
        return
    }
    val transition = rememberInfiniteTransition(label = "status_dot")
    val alpha by transition.animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "status_dot_alpha",
    )
    Box(Modifier.size(8.dp).alpha(alpha).clip(CircleShape)
        .background(MaterialTheme.colorScheme.primary))
}

@Composable
private fun DayHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            .semantics { contentDescription = "日期分组：$label" },
    )
}

@Composable
private fun ActivityCard(
    activity: AgentActivity,
    zoneId: ZoneId,
    onClick: () -> Unit,
) {
    val time = formatTime(activity.timestampMillis, zoneId)
    val spoken = buildString {
        append("${activity.agentName}，${activity.type.label}，${activity.title}。")
        append(activity.summary)
        append(" 时间 $time。")
        activity.goalTitle?.let { append(" 属于目标：$it。") }
    }
    Card(
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = activity.goalId != null) { onClick() }
            .semantics { contentDescription = spoken },
        shape = RoundedCornerShape(12.dp),   // 圆角/颜色换 Hark 主题时改这里
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(activity.type.label, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(time, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(activity.title, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold)
            if (activity.summary.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(activity.summary, style = MaterialTheme.typography.bodyMedium)
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(activity.agentName, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                activity.goalTitle?.let {
                    Spacer(Modifier.width(8.dp))
                    Text("· $it", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---------- 5. 示例数据（预览/自测用，上线删掉或放 debug 源集） ----------

fun sampleActivities(nowMillis: Long = System.currentTimeMillis()): List<AgentActivity> = listOf(
    AgentActivity(
        id = "evt_sample_1", agentName = "mines", type = ActivityType.FINDING,
        title = "发现未授权接口",
        summary = "去掉登录态直接请求拿到了真实数据，已交 triage 判断。",
        timestampMillis = nowMillis - 5 * 60 * 1000,
        goalId = "goal_bugbounty", goalTitle = "漏洞盒子挖洞",
    ),
    AgentActivity(
        id = "evt_sample_2", agentName = "Hassn", type = ActivityType.WAITING_APPROVAL,
        title = "报告已写好，等你点头提交",
        summary = "截图已嵌进正文，点这张卡看报告全文。",
        timestampMillis = nowMillis - 32 * 60 * 1000,
        goalId = "goal_bugbounty", goalTitle = "漏洞盒子挖洞",
    ),
    AgentActivity(
        id = "evt_sample_3", agentName = "cue", type = ActivityType.HANDOFF,
        title = "回款台账已更新",
        summary = "新增 1 条待审阅，本周小结已生成。",
        timestampMillis = nowMillis - 26 * 60 * 60 * 1000,
    ),
)
