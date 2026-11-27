package com.openminis.app.ui.muse

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * [fork/muse] Timeline card model. [kind] selects the icon/asset + actions:
 *   alert  04 账号异常（橙色告警 + 一键修复入口）
 *   x      03/08 X 帖子（蓝色 HN 头像 + 转发/评论/书签）
 *   build  05 GitHub 构建通过（绿勾）
 *   btc    03/08 BTC 卡（btc.png 走势图缩略）
 *   ok     已安装/通过（绿 已安装 徽标）
 */
data class MuseCard(
    val id: String,
    val kind: String,
    val title: String,
    val sub: String,
    val meta: String,
    val time: String,
) {
    companion object {
        fun alert(title: String, sub: String, time: String) =
            MuseCard(id = "alert", kind = "alert", title = title, sub = sub, meta = "令牌", time = time)
        fun post(title: String, sub: String, meta: String, time: String, kind: String) =
            MuseCard(id = "$kind-$title", kind = kind, title = title, sub = sub, meta = meta, time = time)
    }
}

/** Chip filter → which card kinds stay visible. */
fun timelineCards(chip: String?): List<MuseCard> = when (chip) {
    "账号异常" -> listOf(
        MuseCard.alert(
            "Cloudflare 令牌失效",
            "我来修：令牌 Mines-2026-10-12 已过期，DNS 与 Workers 都靠它。",
            "今天 08:12",
        ),
    )
    "X" -> listOf(
        MuseCard.post(
            "Claude Code 画架构图的新技能",
            "只出一个 HTML 文件：你说它不理解的依赖，生成后直接在浏览器打开。",
            "@catrinyalaverty · 9 赞 1,200", "昨天", "x",
        ),
        MuseCard.post(
            "BTC 现货 ETF 两天净流出约 7.3 亿美元",
            "资金费率转负，短线情绪谨慎。",
            "@cryptodaily · 币圈快讯", "5 小时前", "btc",
        ),
    )
    "GitHub" -> listOf(
        MuseCard.post(
            "Mines 安装包构建通过了",
            "build-35 连续第 110 次构建运行，用时 4 分 32 秒。",
            "GitHub 仓库 · workflow", "昨天", "build",
        ),
        MuseCard.post(
            "PR #112 已合并：sidebar 分时归档",
            "feat/sidebar-sections · 3 个测试全绿。",
            "dl1991/mib · merged", "周一", "ok",
        ),
    )
    "值得关注" -> listOf(
        MuseCard.post(
            "nic.com.cn 域名申请",
            "还在工商审核，预计 7 天。",
            "工单 #4821", "周一", "ok",
        ),
        MuseCard.post(
            "BTC 现货 ETF 两天净流出约 7.3 亿美元",
            "资金费率转负，短线情绪谨慎。",
            "@cryptodaily · 币圈快讯", "5 小时前", "btc",
        ),
    )
    else -> emptyList()
}

@Composable
private fun museAsset(path: String): Bitmap? = androidx.compose.runtime.remember(path) {
    val context = LocalContext.current
    runCatching { context.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it) } }
        .getOrNull()
}

/** 顶栏头像：发光圆环（design 01-11 通用）。 */
@Composable
fun MuseAvatar(t: MuseTheme, size: Int = 56) {
    val bmp = museAsset("muse/avatar.png")
    val dp = size.dp
    Box(
        Modifier
            .size(dp)
            .drawBehind {
                // glow ring
                drawCircle(
                    color = MuseColors.Blue.copy(alpha = if (t.dark) 0.55f else 0.35f),
                    style = Stroke(width = 3f),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(dp - 6.dp)
                    .clip(CircleShape),
            )
        } else {
            Text(
                "M",
                color = t.ink,
                fontSize = (size * 0.42).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .size(dp)
                    .clip(CircleShape)
                    .background(t.chip),
            )
        }
    }
}

/** 时间线卡片视图：点击 onOpen，长按 onLongPress（07 菜单）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MuseCardView(
    card: MuseCard,
    t: MuseTheme,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(t.aiCard)
            .border(1.dp, t.hairline, RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
    ) {
        CardIcon(card.kind, t)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                card.title,
                color = t.ink,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (card.sub.isNotBlank()) {
                Text(
                    card.sub,
                    color = t.inkMuted,
                    fontSize = 12.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Text(
                card.meta,
                color = t.inkMuted,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                card.time,
                color = t.inkMuted,
                fontSize = 11.5.sp,
            )
            if (card.kind == "alert") {
                Text(
                    "修复",
                    color = MuseColors.OnBlue,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(t.accent)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            if (card.kind == "build" || card.kind == "ok") {
                Text(
                    "已安装",
                    color = MuseColors.Success,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MuseColors.SuccessDim)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
    if (card.kind == "btc") {
        // 行情缩略图贴卡下（design 03/08 卡带绿色走势图）
        val chart = museAsset("muse/btc.png")
        if (chart != null) {
            Image(
                bitmap = chart.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 2.dp)
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}

@Composable
private fun CardIcon(kind: String, t: MuseTheme) {
    val bg = when (kind) {
        "alert" -> MuseColors.Warn
        "x" -> MuseColors.Blue
        "build" -> MuseColors.Success
        "btc" -> Color(0xFFB7791F)
        else -> MuseColors.Success
    }
    val glyph = when (kind) {
        "alert" -> "!"
        "x" -> "𝕏"
        "build" -> "✓"
        "btc" -> "₿"
        else -> "✓"
    }
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (kind == "alert") MuseColors.WarnDim else t.chip),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            color = bg,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
