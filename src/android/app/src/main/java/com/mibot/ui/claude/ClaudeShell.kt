package com.mibot.ui.claude

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mibot.chat.ConversationSummary
import kotlin.math.cos
import kotlin.math.sin

/**
 * Claude 主壳 —— 复刻 com.anthropic.claude 的界面骨架（真机截图 + 反编译源码）。
 *
 * 结构：顶栏（`≡` + 标题 + Claude 图标）+ 会话侧栏 + 主内容 + 输入栏。
 * 参考 app/main/MainAppScreens + app/main/loggedin/LoggedInScreens（LoggedIn → ClaudeApp）。
 */
@Composable
fun ClaudeShell(
    chatContent: @Composable () -> Unit,
    sessions: List<ConversationSummary> = emptyList(),
    onOpenSession: (String) -> Unit = {},
    onDeleteSession: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    showSidebar: Boolean = true,
    onMoreEntry: (com.mibot.ui.hq.HqMoreEntry) -> Unit = {},
) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(Claude.bg),
    ) {
        // 手机(窄屏)全宽聊天:侧栏折叠成汉堡弹出;桌面(宽屏)才并排侧栏。
        val wide = maxWidth >= 600.dp
        val sidebarVisible = showSidebar && wide
        val showMore = remember { mutableStateOf(false) }
        if (sidebarVisible) {
            Row(Modifier.fillMaxSize()) {
                ClaudeSidebar(
                    sessions = sessions,
                    onOpenSession = onOpenSession,
                    onDeleteSession = onDeleteSession,
                    modifier = Modifier.width(300.dp),
                )
                DividerVLine(modifier = Modifier.fillMaxHeight())
                Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                    ClaudeTopBar(modifier = Modifier.fillMaxWidth(), onMenu = { showMore.value = true }, onSpark = { showMore.value = true })
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(Claude.inputRadius))) {
                        chatContent()
                    }
                    ClaudeInputBar(modifier = Modifier.fillMaxWidth())
                }
            }
        } else {
            // 窄屏:顶栏(汉堡开侧栏) + 全宽聊天 + 底部横排输入栏
            Column(modifier = Modifier.fillMaxSize()) {
                ClaudeTopBar(
                    modifier = Modifier.fillMaxWidth(),
                    onMenu = { showMore.value = true },
                    onSpark = { showMore.value = true },
                )
                Box(modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(Claude.inputRadius))) {
                    chatContent()
                }
                ClaudeInputBar(modifier = Modifier.fillMaxWidth())
            }
        }
        if (showMore.value) {
            ClaudeMoreMenu(
                onEntry = { e ->
                    showMore.value = false
                    onMoreEntry(e)
                },
                onDismiss = { showMore.value = false },
            )
        }
    }
}

/** 会话侧栏（Claude 侧栏：hamburger 触发 + 会话列表）。 */
@Composable
fun ClaudeSidebar(
    sessions: List<ConversationSummary>,
    onOpenSession: (String) -> Unit = {},
    onDeleteSession: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Claude.bgAlt),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClaudeHamburger(modifier = Modifier.size(20.dp), color = Claude.ink2)
            Spacer(Modifier.width(12.dp))
            Text("Claude", style = ClaudeType.RowTitle)
        }
        HorizontalDivider(thickness = 1.dp, color = Claude.edge)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(sessions, key = { it.id }) { s ->
                ClaudeSessionRow(s = s, onClick = { onOpenSession(s.id) }, onDelete = { onDeleteSession(s.id) })
            }
        }
    }
}

/** 顶栏：左上 `≡` + 中间 Claude wordmark + 右上橙 asterisk（可点 = 打开更多）。 */
@Composable
fun ClaudeTopBar(
    modifier: Modifier = Modifier,
    onMenu: () -> Unit = {},
    onSpark: () -> Unit = {},
    title: String? = null,
) {
    Row(
        modifier = modifier
            .statusBarsPadding()
            .background(Claude.bg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ClaudeHamburger(modifier = Modifier.size(20.dp), color = Claude.ink2, onClick = onMenu)
        // 中间 wordmark：Claude 官方居中标题，缺了会让两端"飘"。
        Text(
            text = title ?: "Claude",
            style = ClaudeType.RowTitle.copy(fontWeight = FontWeight.SemiBold),
            color = Claude.ink,
        )
        ClaudeSparkIcon(
            size = 22.dp,
            color = Claude.ink2,
            onClick = onSpark,   // 右上 asterisk 可点 = 打开更多菜单
        )
    }
}

/** 竖分隔线（侧栏和主区之间）。 */
@Composable
private fun DividerVLine(modifier: Modifier = Modifier) {
    Box(modifier = modifier.width(1.dp).background(Claude.edge))
}

/** Claude 风格"更多"功能菜单：覆盖层 + 分组列表（Omega 功能入口）。 */
@Composable
fun ClaudeMoreMenu(
    onEntry: (com.mibot.ui.hq.HqMoreEntry) -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 极简菜单：只核心项 + 「更多功能」进全量页（去掉与更多页重复的长列表）
    val core = listOf(
        "bots" to "机器人",
        "settings" to "设置",
        "models" to "模型",
        "usage" to "用量",
    )
    androidx.compose.foundation.layout.Box(
        modifier = modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(Claude.cardRadius))
                .background(Claude.surface)
                .padding(vertical = 14.dp),
        ) {
            Text("More", style = ClaudeType.ScreenTitle, color = Claude.ink, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            Spacer(Modifier.height(6.dp))
            core.forEach { (id, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(Claude.cardRadius))
                        .clickable { onEntry(com.mibot.ui.hq.HqMoreEntry(id, label, com.mibot.ui.theme.MikaIcons.Gear, Claude.ink)) }
                        .padding(horizontal = 12.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = ClaudeType.Body, color = Claude.ink)
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(1.dp).background(Claude.edge))
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(Claude.cardRadius))
                    .clickable { onEntry(com.mibot.ui.hq.HqMoreEntry("more", "更多", com.mibot.ui.theme.MikaIcons.Gear, Claude.ink)) }
                    .padding(horizontal = 12.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("更多功能", style = ClaudeType.Body, color = Claude.ink, modifier = Modifier.weight(1f))
                Text("›", style = ClaudeType.Body, color = Claude.ink3)
            }
        }
    }
}

/** 会话行（Claude 侧栏项）。 */
@Composable
fun ClaudeSessionRow(
    s: ConversationSummary,
    onClick: () -> Unit = {},
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Claude.cardRadius))
            .background(if (selected) Claude.surface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = s.title.ifBlank { "新对话" },
                style = ClaudeType.RowTitle,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = claudeWhen(s.updatedAt),
                style = ClaudeType.Caption,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(12.dp))
        ClaudeSparkIcon(size = 14.dp, color = Claude.ink3)
    }
}

/** 空态：居中橙色 asterisk + 建议提问（对应 Claude 的"月光下的对话？"）。 */
@Composable
fun ClaudeEmptyState(
    prompt: String,
    suggestions: List<String> = emptyList(),
    onSuggestion: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ClaudeSparkIcon(size = 64.dp, color = Claude.accent)
        Spacer(Modifier.height(28.dp))
        Text(
            text = prompt,
            style = ClaudeType.ScreenTitle.copy(fontWeight = FontWeight.Normal, textAlign = TextAlign.Center),
        )
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(36.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                suggestions.forEach { s ->
                    ClaudeSuggestionPill(text = s, onClick = { onSuggestion(s) })
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

/** 建议提问胶囊。 */
@Composable
fun ClaudeSuggestionPill(text: String, onClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Claude.buttonRadius))
            .background(Claude.suggestion)
            .border(1.dp, Claude.edge, RoundedCornerShape(Claude.buttonRadius))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = ClaudeType.Body.copy(fontSize = 14.sp))
    }
}

/** Claude asterisk ✳（品牌标志，8 尖放射）。 */
@Composable
fun ClaudeSparkIcon(
    size: androidx.compose.ui.unit.Dp = 24.dp,
    color: Color = Claude.accent,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Canvas(modifier = modifier.size(size).clickable(onClick = onClick)) {
        val cx = size.value / 2f
        val cy = size.value / 2f
        val len = cx * 0.92f
        val armWidth = size.value * 0.16f
        repeat(8) { i ->
            val a = (i * 45.0) * (Math.PI / 180.0)
            val dx = (len * cos(a)).toFloat()
            val dy = (len * sin(a)).toFloat()
            drawLine(
                color = color,
                start = Offset(cx - dx, cy - dy),
                end = Offset(cx + dx, cy + dy),
                strokeWidth = armWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** 顶栏菜单图标 `≡`（Claude 用两横线）。 */
@Composable
fun ClaudeHamburger(
    size: androidx.compose.ui.unit.Dp = 20.dp,
    color: Color = Claude.ink2,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Canvas(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(Claude.buttonRadius))
            .clickable(onClick = onClick)
            .padding(2.dp),
    ) {
        val w = size.value - 4f
        val bar = size.value * 0.09f
        drawLine(color, Offset(0f, w * 0.32f), Offset(w, w * 0.32f), strokeWidth = bar, cap = StrokeCap.Round)
        drawLine(color, Offset(0f, w * 0.72f), Offset(w, w * 0.72f), strokeWidth = bar, cap = StrokeCap.Round)
    }
}

/** 把会话时间格式成相对文案。 */
private fun claudeWhen(ts: Long): String {
    if (ts <= 0L) return "刚刚"
    val sec = ((System.currentTimeMillis() - ts) / 1000L).coerceAtLeast(0)
    return when {
        sec < 60 -> "刚刚"
        sec < 3600 -> "${sec / 60} 分钟前"
        sec < 86400 -> "${sec / 3600} 小时前"
        sec < 604800 -> "${sec / 86400} 天前"
        else -> "${ts / 1000 / 86400 / 365} 年前"
    }
}
