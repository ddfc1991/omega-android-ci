package com.openminis.app.mibot.gateway

import android.content.Context
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openminis.app.MinisApp
import com.openminis.app.mibot.design.MibotType
import com.openminis.app.mibot.design.t
import com.openminis.app.ui.glass.GlassPalette
import com.openminis.app.ui.glass.GlassStyle
import com.openminis.app.ui.glass.LocalGlassStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [fork/magpie-gateway] The Magpie tab: gateway status, the model list in
 * probe order (alive + fastest first), and the manual re-check button.
 * The daily auto-probe runs from MinisApp; this page is where the user
 * watches it happen.
 */
@Composable
fun MagpiePage() {
    val ctx = LocalContext.current
    val app = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val style = LocalGlassStyle.current
    val dark = style == GlassStyle.DUSK
    val fg = if (dark) GlassPalette.DuskOn else GlassPalette.WarmOn
    val fgMuted = if (dark) GlassPalette.DuskOnMuted else GlassPalette.WarmOnMuted
    val accent = if (dark) GlassPalette.DuskAccent else GlassPalette.WarmAccent

    var snap by remember { mutableStateOf(MagpieHealth.snapshot(app)) }
    var checking by remember { mutableStateOf(false) }
    var gatewayUp by remember { mutableStateOf<Boolean?>(null) }

    // Probe the port once on entry — fast, local, no model cost.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        gatewayUp = withContext(Dispatchers.IO) {
            runCatching {
                java.net.Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", MagpieHealth.GATEWAY_PORT), 2500) }
                true
            }.getOrDefault(false)
        }
    }

    fun recheck() {
        if (checking) return
        checking = true
        scope.launch(Dispatchers.IO) {
            val app0 = app as? MinisApp
            val repo = app0?.providerRepositoryOrNull
            val instance = repo?.instances?.firstOrNull { MagpieHealth.isMagpie(it) }
            if (repo != null && instance != null) {
                runCatching { MagpieHealth.refreshAndCheck(app, repo, instance) }
            }
            gatewayUp = runCatching {
                java.net.Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", MagpieHealth.GATEWAY_PORT), 2500) }
                true
            }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                snap = MagpieHealth.snapshot(app)
                checking = false
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        // ── status card ────────────────────────────────────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(fgMuted.copy(alpha = 0.08f))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when (gatewayUp) {
                                true -> Color(0xFF4CAF50)
                                false -> Color(0xFFE53935)
                                null -> fgMuted
                            },
                        ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when (gatewayUp) {
                        true -> t("网关在线", "Gateway online")
                        false -> t("网关离线", "Gateway offline")
                        null -> t("检测中…", "Checking…")
                    },
                    color = fg,
                    fontSize = MibotType.Body,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "127.0.0.1:${MagpieHealth.GATEWAY_PORT}",
                    color = fgMuted,
                    fontSize = MibotType.Caption,
                )
            }
            Spacer(Modifier.height(10.dp))
            // [fork/magpie-gateway] t()/ago() are @Composable: call them in the
            // composable scope, never inside buildString's plain lambda.
            val alive = snap?.probes?.values?.count { it.ok } ?: 0
            val total = snap?.probes?.size ?: 0
            val summary = t("${alive} 个模型可用 / 共 ${total} 个", "$alive alive / $total models")
            val lastCheck = snap?.lastCheck ?: 0L
            val checkedPrefix = t("体检于 ", "checked ")
            val agoText = if (lastCheck > 0) ago(lastCheck) else ""
            val checked = if (lastCheck > 0) " · " + checkedPrefix + agoText else ""
            Text(
                summary + checked,
                color = fgMuted,
                fontSize = MibotType.Caption,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MagpieButton(
                    label = if (checking) t("体检中…", "Probing…") else t("立即体检", "Check now"),
                    accent = accent,
                    fg = fg,
                    enabled = !checking && gatewayUp == true,
                ) { recheck() }
                if (gatewayUp == false) {
                    MagpieButton(
                        label = t("启动 Magpie", "Launch Magpie"),
                        accent = accent,
                        fg = fg,
                        enabled = true,
                    ) {
                        runCatching {
                            ctx.packageManager.getLaunchIntentForPackage("com.magpie.android")
                                ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                ?.let { ctx.startActivity(it) }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── model list (probe order) ───────────────────────────────────
        val probes = snap?.probes?.values?.toList().orEmpty()
        if (probes.isEmpty()) {
            // [fork/ux-empty-states] Full empty state: icon + title + reason +
            // one primary action, not a bare "暂无数据" line.
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    com.openminis.app.mibot.design.Glyph(
                        com.openminis.app.mibot.design.LineIcons.Activity,
                        size = 28.dp,
                        tint = accent,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    t("还没有体检记录", "No health check yet"),
                    color = fg,
                    fontSize = MibotType.Body,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    t("Mibot 每天自动刷新模型并体检；也可以现在手动跑一次，通的快的会排在最前面。",
                      "Mibot refreshes and checks models daily, or run a pass now — working, fast models sort first."),
                    color = fgMuted,
                    fontSize = MibotType.Caption,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(16.dp))
                MagpieButton(
                    label = t("立即体检", "Check now"),
                    accent = accent,
                    fg = fg,
                    enabled = gatewayUp == true && !checking,
                ) { recheck() }
            }
        } else {
            val ordered = probes.sortedWith(
                compareBy({ if (it.ok) 0 else 1 }, { if (it.ok) it.ms else Long.MAX_VALUE }),
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(ordered, key = { it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (p.ok) Color(0xFF4CAF50) else fgMuted.copy(alpha = 0.4f)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            p.id,
                            color = if (p.ok) fg else fgMuted,
                            fontSize = MibotType.Body,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        if (p.ok && p.ms >= 0) {
                            Text(
                                "${p.ms}ms",
                                color = when {
                                    p.ms < 1500 -> Color(0xFF4CAF50)
                                    p.ms < 4000 -> fgMuted
                                    else -> Color(0xFFE57373)
                                },
                                fontSize = MibotType.Caption,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MagpieButton(label: String, accent: Color, fg: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) accent.copy(alpha = 0.16f) else fg.copy(alpha = 0.06f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, color = if (enabled) accent else fg.copy(alpha = 0.45f), fontSize = MibotType.Body)
    }
}

@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
private fun ago(then: Long): String {
    val s = (System.currentTimeMillis() - then) / 1000
    return when {
        s < 60 -> t("刚刚", "just now")
        s < 3600 -> t("${s / 60} 分钟前", "${s / 60}m ago")
        s < 86400 -> t("${s / 3600} 小时前", "${s / 3600}h ago")
        else -> t("${s / 86400} 天前", "${s / 86400}d ago")
    }
}
