package com.openminis.app.ui.muse

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.data.db.ChatSessionEntity
import com.openminis.app.data.repository.ChatRepository

/** [fork/muse] Loads a bundled Muse asset once. */
@Composable
private fun museBitmap(path: String): android.graphics.Bitmap? = remember(path) {
    val context = LocalContext.current
    runCatching {
        context.assets.open(path).use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}

/**
 * [fork/muse] 01/02 首页 (light/dark) fused with 03-08 消息 timeline:
 * greeting header + filter chips + card timeline + 最近对话 (real sessions).
 * Card long-press opens the 07 「…」菜单; alert cards open the 06 详情弹层.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MuseFeedScreen(
    dark: Boolean,
    chatRepository: ChatRepository,
    onOpenSession: (String) -> Unit,
    onChatDraft: (String) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenCards: () -> Unit,
) {
    val t = MuseTheme.resolve(dark)
    var chip by remember { mutableStateOf<String?>(null) }
    var alertOpen by remember { mutableStateOf(false) }
    var menuCard by remember { mutableStateOf<MuseCard?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(t.base),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 顶栏：☰ / 头像+Mines / ⚙
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Menu, null,
                    tint = t.inkMuted, modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.weight(1f))
                MuseAvatar(t)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Mines",
                        color = t.ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MuseColors.Success),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "正在陪你写 GitHub",
                            color = t.inkMuted,
                            fontSize = 11.sp,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Outlined.Settings, null,
                    tint = t.inkMuted, modifier = Modifier.size(26.dp),
                )
            }
        }
        // 问候 + AI 提示行
        item {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 2.dp)) {
                Text(
                    greeting(),
                    color = t.ink,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
                Text(
                    "Cloudflare 令牌失效了，点一下就能恢复。",
                    color = t.inkMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        // 筛选胶囊行
        item {
            val chips = listOf(
                "账号异常" to 1, "X" to 12, "GitHub" to 5, "值得关注" to 3,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chips.forEach { (label, count) ->
                    val on = chip == label
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (on) t.accent else t.chip)
                            .border(0.dp, Color.Transparent)
                            .clickable { chip = if (on) null else label }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(
                            "$label $count",
                            color = if (on) MuseColors.OnBlue else t.inkMuted,
                            fontSize = 12.5.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
        // 时间线卡片
        val cards = timelineCards(chip).ifEmpty {
            listOf(
                MuseCard.alert(
                    "Cloudflare 令牌失效",
                    "我来修：令牌 Mines-2026-10-12 已过期，DNS 与 Workers 都靠它，修好前这条服务会持续报错。",
                    "今天 08:12",
                ),
                MuseCard.post(
                    "Claude Code 画架构图的新技能",
                    "只出一个 HTML 文件：你说它不理解的依赖，生成后直接在浏览器打开。",
                    "@catrinyalaverty · 9 赞 1,200",
                    "昨天",
                    kind = "x",
                ),
                MuseCard.post(
                    "Mines 安装包构建通过了",
                    "build-35 连续第 110 次构建运行，用时 4 分 32 秒。",
                    "GitHub 仓库 · workflow",
                    "昨天",
                    kind = "build",
                ),
                MuseCard.post(
                    "BTC 现货 ETF 两天净流出约 7.3 亿美元",
                    "资金费率转负，短线情绪谨慎。",
                    "@cryptodaily · 币圈快讯",
                    "5 小时前",
                    kind = "btc",
                ),
                MuseCard.post(
                    "nic.com.cn 域名申请",
                    "还在工商审核，预计 7 天。",
                    "工单 #4821",
                    "周一",
                    kind = "ok",
                ),
            )
        }
        items(cards, key = { it.id }) { card ->
            MuseCardView(
                card = card,
                theme = t,
                onClick = {
                    when (card.kind) {
                        "alert" -> alertOpen = true
                        "x" -> onChatDraft("讨论这条 X 帖子：${card.title}")
                        "build" -> onChatDraft("看看 build-35 构建日志")
                        "btc" -> onChatDraft("分析一下 BTC 现货 ETF 资金流")
                        else -> onChatDraft(card.title)
                    }
                },
                onLongPress = { menuCard = card },
            )
        }
        // 最近对话（真实会话）
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("最近对话", color = t.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("全部", color = t.accent, fontSize = 13.sp)
            }
        }
        items(rememberSessions(chatRepository), key = { "s" + it.id }) { s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(t.aiCard)
                    .border(1.dp, t.hairline, RoundedCornerShape(16.dp))
                    .clickable { onOpenSession(s.id) }
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(t.chip),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        (s.title ?: "聊").take(1).uppercase(),
                        color = t.inkMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        s.title ?: "新对话",
                        color = t.ink,
                        fontSize = 14.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        s.lastPreview ?: "",
                        color = t.inkMuted,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    relativeTime(s.updatedAtMs),
                    color = t.inkMuted,
                    fontSize = 11.5.sp,
                )
            }
        }
    }

    if (alertOpen) MuseDetailSheet(t, onDismiss = { alertOpen = false }, onFix = {
        alertOpen = false
        onChatDraft("修复 Cloudflare 令牌：Mines-2026-10-12 已过期，重签并刷新 DNS 与 Workers。")
    })
    menuCard?.let { card ->
        MuseItemMenu(t, card = card, onDismiss = { menuCard = null })
    }
}

private fun greeting(): String = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "上午好"
    in 12..17 -> "下午好"
    in 18..23 -> "晚上好"
    else -> "夜深了"
}

private fun relativeTime(ms: Long): String {
    val delta = System.currentTimeMillis() - ms
    return when {
        delta < 60_000 -> "现在"
        delta < 3_600_000 -> "${delta / 60_000} 分"
        delta < 86_400_000 -> "${delta / 3_600_000} 小时"
        delta < 2 * 86_400_000 -> "昨天"
        else -> "${delta / 86_400_000} 天前"
    }
}

/** Minimal read model over the session store for the 最近对话 block. */
private data class SessionRow(
    val id: String,
    val title: String?,
    val lastPreview: String?,
    val updatedAtMs: Long,
)

@Composable
private fun rememberSessions(chatRepository: ChatRepository): List<SessionRow> {
    var rows by remember { mutableStateOf(emptyList<SessionRow>()) }
    LaunchedEffect(chatRepository) {
        runCatching {
            chatRepository.dao.listSessions()
                .filter { !it.isChild }
                .take(8)
                .map { s ->
                    SessionRow(
                        id = s.id,
                        title = s.title,
                        lastPreview = s.lastMessage,
                        updatedAtMs = s.updatedAt,
                    )
                }
        }.getOrNull()?.let { rows = it }
    }
    return rows
}

/** 06 详情弹层：一键修复 */
@Composable
fun MuseDetailSheet(
    t: MuseTheme,
    onDismiss: () -> Unit,
    onFix: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = t.sheet,
        hideHandle = true,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Cloudflare 令牌失效：我来修",
                color = t.ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "令牌 Mines-2026-10-12-2140 已过期，DNS 与 Workers 都靠它。重签后跑一遍验证即可恢复。",
                color = t.inkMuted,
                fontSize = 14.sp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                row(t, "修复会做的事", "按源令牌密钥重签 · 今天 10:34 · 只读不变")
                row(t, "测试通过才替换", "验证通过前保留旧令牌 · 7 天后可再恢复")
                row(t, "帮你找到旧令牌", "已打开令牌页，旧令牌已高亮")
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(t.raised)
                        .clickable { onDismiss() }
                        .size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.MoreVert, null, tint = t.inkMuted, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "一键修复",
                    color = MuseColors.OnBlue,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(t.accent)
                        .clickable { onFix() }
                        .height(48.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun row(t: MuseTheme, title: String, sub: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(t.raised)
            .padding(12.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MuseColors.Success),
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(end = 10.dp),
        )
        Column {
            Text(title, color = t.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = t.inkMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** 07 「…」菜单 · 图标分组 */
@Composable
fun MuseItemMenu(
    t: MuseTheme,
    card: MuseCard,
    onDismiss: () -> Unit,
) {
    val items = listOf(
        "喜欢，多推这类" to "IconThumbUp",
        "不感兴趣" to "IconThumbDown",
        "不显示该来源" to "IconSourceOff",
        "打开源网页" to "IconGlobe",
        "置顶" to "IconPin",
        "分享" to "IconShare",
    )
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = t.sheet,
        hideHandle = true,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            items.forEach { (label, _) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onDismiss() }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(t.raised),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("•", color = t.inkMuted, fontSize = 18.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(label, color = t.ink, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "取消",
                color = t.inkMuted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(t.raised)
                    .clickable { onDismiss() }
                    .height(50.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
