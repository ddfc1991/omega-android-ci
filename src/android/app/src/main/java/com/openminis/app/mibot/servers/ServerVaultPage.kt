package com.openminis.app.mibot.servers

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.mibot.design.t
import com.openminis.app.ui.glass.LocalGlassBottomInset
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 服务器 · 草台舰队 dashboard（暗底 + 青柠绿 + 分区 + hero + 柱状图）。
 *
 * 数据源：/var/minis/shared/servers.json（宿主侧 filesDir/minis-global/shared/），
 * 缺失时回退到旧 SharedPreferences 清单（ServerVaultStore）。
 * 机器 hostname 不可中文；中文显示名走 display_name 字段。
 */

private val C_BG = Color(0xFF0B0C0E)
private val C_PANEL = Color(0xFF16181B)
private val C_PANEL2 = Color(0xFF1C1F22)
private val C_LINE = Color(0x14FFFFFF)
private val C_TXT = Color(0xFFF2F3F0)
private val C_SUB = Color(0xFF9AA1A7)
private val C_DIM = Color(0xFF6B7278)
private val C_LIME = Color(0xFFB6F04B)
private val C_LIME2 = Color(0xFF8FD62F)
private val C_GREEN = Color(0xFF3ECF6E)
private val C_RED = Color(0xFFF25C5C)
private val R = RoundedCornerShape(18.dp)
private val R_SM = RoundedCornerShape(10.dp)

// ── fleet data ────────────────────────────────────────────────────────────

data class FleetServer(
    val name: String,
    val displayName: String,
    val kind: String,
    val os: String,
    val location: String,
    val size: String,
    val cores: Int,
    val memGb: Int,
    val diskGb: Int,
    val host: String,
    val sshPort: Int,
    val status: String,
) {
    val show: String get() = displayName.ifBlank { name }
}

data class Fleet(
    val poolCores: Int,
    val poolMem: Int,
    val poolDisk: Int,
    val servers: List<FleetServer>,
) {
    val usedCores: Int get() = servers.sumOf { it.cores }
    val usedMem: Int get() = servers.sumOf { it.memGb }
    val usedDisk: Int get() = servers.sumOf { it.diskGb }
    val online: Int get() = servers.count { it.status.equals("Running", true) }
}

private fun parseFleet(raw: String): Fleet? = try {
    val o = JSONObject(raw)
    val pool = o.optJSONObject("pool")
    val list = mutableListOf<FleetServer>()
    val accounts = o.optJSONArray("accounts") ?: JSONArray()
    for (i in 0 until accounts.length()) {
        val acc = accounts.getJSONObject(i)
        val srv = acc.optJSONArray("servers") ?: continue
        for (j in 0 until srv.length()) {
            val s = srv.getJSONObject(j)
            list.add(
                FleetServer(
                    name = s.optString("name"),
                    displayName = s.optString("display_name"),
                    kind = s.optString("kind"),
                    os = s.optString("os"),
                    location = s.optString("location"),
                    size = s.optString("size"),
                    cores = s.optInt("cores", 1),
                    memGb = s.optInt("memory_gb", 2),
                    diskGb = s.optInt("disk_gb", 10),
                    host = s.optString("host"),
                    sshPort = s.optInt("ssh_port", 22),
                    status = s.optString("status"),
                ),
            )
        }
    }
    if (list.isEmpty()) null else Fleet(
        poolCores = pool?.optInt("cores", 4) ?: 4,
        poolMem = pool?.optInt("memory_gb", 12) ?: 12,
        poolDisk = pool?.optInt("disk_gb", 50) ?: 50,
        servers = list,
    )
} catch (e: Exception) {
    null
}

private fun loadFleet(ctx: Context): Fleet? = try {
    val f = File(ctx.filesDir, "minis-global/shared/servers.json")
    if (f.exists()) parseFleet(f.readText()) else null
} catch (e: Exception) {
    null
}

// 旧清单回退（Tailscale 手填条目）
private fun legacyFleet(ctx: Context): Fleet = try {
    val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
    val a = JSONArray(raw)
    val list = (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        FleetServer(
            name = o.optString("name"),
            displayName = o.optString("name"),
            kind = "tailscale",
            os = o.optString("note"),
            location = "",
            size = "",
            cores = 0, memGb = 0, diskGb = 0,
            host = o.optString("address"),
            sshPort = 22,
            status = "",
        )
    }
    Fleet(4, 12, 50, list)
} catch (e: Exception) {
    Fleet(4, 12, 50, emptyList())
}

// ── dashboard ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerVaultPage(
    onBack: () -> Unit,
    onDiscuss: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val bottom = LocalGlassBottomInset.current
    val clip = LocalClipboardManager.current
    val fleet = remember { loadFleet(ctx) ?: legacyFleet(ctx) }
    val online = fleet.online
    val total = fleet.servers.size

    Scaffold(
        containerColor = C_BG,
        topBar = {
            TopAppBar(
                title = {
                    Text(t("服务器 · 草台舰队", "Servers · Caotai Fleet"), fontWeight = FontWeight.SemiBold, color = C_TXT)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = C_TXT)
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0x1AB6F04B))
                            .border(1.dp, Color(0x40B6F04B), RoundedCornerShape(999.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Box(Modifier.width(7.dp).height(7.dp).clip(RoundedCornerShape(50)).background(C_LIME))
                        Text("$online/$total " + t("在线", "online"), color = C_LIME, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = bottom + 28.dp),
        ) {
            Text(t("switched.cloud · 草台班子", "switched.cloud · ragtag fleet"), color = C_SUB, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))

            ZoneLabel(t("总览", "Overview"))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .weight(1.5f)
                        .clip(R)
                        .background(C_PANEL)
                        .border(1.dp, C_LINE, R)
                        .padding(16.dp),
                ) {
                    Column {
                        Text(t("资源池使用", "Pool usage"), color = C_SUB, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        val pct = if (fleet.poolCores > 0) fleet.usedCores * 100 / fleet.poolCores else 0
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$pct", color = C_TXT, fontSize = 38.sp, fontWeight = FontWeight.Black, lineHeight = 40.sp)
                            Text("%", color = C_SUB, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 3.dp, bottom = 5.dp))
                        }
                        GradientBar(pct / 100f)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${fleet.usedCores}/${fleet.poolCores} " + t("核", "cores"), color = C_DIM, fontSize = 11.sp)
                            Text(t("内存", "mem") + " ${fleet.usedMem}/${fleet.poolMem} GB", color = C_DIM, fontSize = 11.sp)
                        }
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .clip(R)
                        .background(Brush.linearGradient(listOf(C_LIME, Color(0xFF9BE43A))))
                        .padding(16.dp),
                ) {
                    Column {
                        Text(t("剩余运行时间", "Runtime left"), color = Color(0x99111111), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${online * 142}", color = Color(0xFF0B0C0E), fontSize = 36.sp, fontWeight = FontWeight.Black, lineHeight = 38.sp)
                            Text("h", color = Color(0x99111111), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 3.dp, bottom = 4.dp))
                        }
                        Text(t("每台每小时 -1h", "-1h per server / hour"), color = Color(0xB3111111), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            ZoneLabel(t("容量", "Capacity"))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatChip(Modifier.weight(1f), "${fleet.usedCores}/${fleet.poolCores}", t("CPU 核", "CPU cores"))
                StatChip(Modifier.weight(1f), "${fleet.usedMem}/${fleet.poolMem}", t("内存 GB", "Memory GB"))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatChip(Modifier.weight(1f), "${fleet.usedDisk}/${fleet.poolDisk}", t("磁盘 GB", "Disk GB"))
                StatChip(Modifier.weight(1f), "$online/$total", t("可用账号", "Accounts"))
            }

            ZoneLabel(t("运行时间", "Runtime"))
            Box(Modifier.fillMaxWidth().clip(R).background(C_PANEL).border(1.dp, C_LINE, R).padding(16.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(t("各服务器运行", "Servers runtime"), color = C_TXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(t("共", "total") + " $online " + t("台在线", "online"), color = C_SUB, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth().height(96.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (fleet.servers.isEmpty()) {
                            Text(t("暂无实例", "No instances"), color = C_DIM, fontSize = 12.sp)
                        }
                        fleet.servers.forEachIndexed { idx, s ->
                            val frac = (0.25f + 0.5f * ((idx * 37) % 100) / 100f).coerceIn(0.12f, 0.85f)
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height((96 * frac).dp)
                                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                                        .background(
                                            if (s.status.equals("Running", true)) Brush.verticalGradient(listOf(C_LIME, C_LIME2))
                                            else Color(0x48B6F04B),
                                        ),
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(s.show.take(11), color = C_DIM, fontSize = 9.sp, maxLines = 1, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }

            ZoneLabel(t("实例", "Instances"))
            fleet.servers.forEach { s ->
                ServerRow(s, clip)
                Spacer(Modifier.height(10.dp))
            }
            if (fleet.servers.isEmpty()) {
                Box(Modifier.fillMaxWidth().clip(R).background(C_PANEL).border(1.dp, C_LINE, R).padding(16.dp)) {
                    Text(t("还没有服务器", "No servers yet"), color = C_SUB, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                t("noprofit.space 限每人一号 · 单号上限 4 台 Starter", "one account per person · up to 4 Starters"),
                color = C_DIM, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ── pieces ────────────────────────────────────────────────────────────────

@Composable
private fun ZoneLabel(text: String) {
    Text(
        text.uppercase(),
        color = C_DIM,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(top = 22.dp, bottom = 10.dp, start = 2.dp),
    )
}

@Composable
private fun GradientBar(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(Color(0x12FFFFFF)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Brush.horizontalGradient(listOf(C_LIME2, C_LIME))),
        )
    }
}

@Composable
private fun StatChip(modifier: Modifier, num: String, label: String) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(C_PANEL)
            .border(1.dp, C_LINE, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                val parts = num.split("/")
                if (parts.size == 2) {
                    Text(parts[0], color = C_TXT, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text("/${parts[1]}", color = C_LIME, fontSize = 20.sp, fontWeight = FontWeight.Black)
                } else {
                    Text(num, color = C_TXT, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(label, color = C_SUB, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ServerRow(s: FleetServer, clip: ClipboardManager) {
    val running = s.status.equals("Running", true)
    val alphaF = if (running || s.status.isBlank()) 1f else 0.55f
    Box(
        Modifier
            .fillMaxWidth()
            .clip(R)
            .background(C_PANEL)
            .border(1.dp, C_LINE, R)
            .padding(14.dp)
            .alpha(alphaF),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(9.dp).height(9.dp).clip(RoundedCornerShape(50)).background(if (running || s.status.isBlank()) C_GREEN else C_RED))
                Spacer(Modifier.width(9.dp))
                Column {
                    Text(s.show, color = C_TXT, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    val sub = when {
                        s.kind == "tailscale" -> if (s.os.isBlank()) "tailscale" else s.os
                        s.kind.isBlank() -> ""
                        else -> "${s.kind} · ${s.os} · ${s.location}"
                    }
                    if (sub.isNotBlank()) Text(sub, color = C_DIM, fontSize = 11.sp)
                }
            }
            if (s.cores > 0) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("${s.cores} " + t("核", "core"), color = C_SUB, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("${s.memGb} GB", color = C_SUB, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("${s.diskGb} GB", color = C_SUB, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (s.size.isNotBlank()) Text(s.size, color = C_DIM, fontSize = 12.sp)
                }
                Spacer(Modifier.height(10.dp))
                MiniBar(t("CPU", "CPU"), 0.08f)
                Spacer(Modifier.height(6.dp))
                MiniBar(t("MEM", "MEM"), 0.12f)
            }
            Spacer(Modifier.height(12.dp))
            if (s.host.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(R_SM)
                        .background(C_PANEL2)
                        .border(1.dp, C_LINE, R_SM)
                        .clickable { clip.setText(AnnotatedString("ssh root@${s.host} -p ${s.sshPort}")) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        "ssh root@${s.host} -p ${s.sshPort}",
                        color = Color(0xFFCDE9A8), fontSize = 12.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(t("复制", "copy"), color = C_LIME, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
                }
            } else if (!running && s.status.isNotBlank()) {
                Text(s.status, color = C_RED, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun MiniBar(label: String, fraction: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = C_DIM, fontSize = 10.sp, modifier = Modifier.width(34.dp))
        Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(99.dp)).background(Color(0x12FFFFFF))) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(5.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Brush.horizontalGradient(listOf(C_GREEN, C_LIME))),
            )
        }
    }
}
