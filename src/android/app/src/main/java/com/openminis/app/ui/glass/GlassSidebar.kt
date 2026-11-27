package com.openminis.app.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.R
import com.openminis.app.mibot.design.MibotIcon
import com.openminis.app.mibot.design.MibotType
import com.openminis.app.mibot.design.t
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/**
 * [fork/glass-ui] The sidebar has one job: the recent list. Chats and bot
 * group chats sit together in a single list; the only other controls are
 * search and new chat.
 *
 * [fork/sidebar-sections] The list is split automatically — Pinned, Today,
 * Yesterday, Previous 7 days, Previous 30 days, and a collapsed Archived
 * section for anything untouched for [RecentItems.ARCHIVE_AFTER_DAYS] days.
 * Each row carries a delete button; deletion always asks first and goes
 * through the same funnel as the session list's own delete
 * ([com.openminis.app.data.session.SessionDeleter] / `GroupChats.delete`),
 * supplied by the caller via [onDelete].
 */
@Composable
fun GlassSidebar(
    items: List<RecentItem>,
    haze: HazeState,
    onOpen: (RecentItem) -> Unit,
    onNewChat: () -> Unit,
    onDelete: (RecentItem) -> Unit,
    // [fork/settings-sidebar] Settings entry at the sidebar bottom; default
    // no-op keeps existing previews/callers compiling.
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val style = LocalGlassStyle.current
    val dark = style == GlassStyle.DUSK
    val fg = if (dark) GlassPalette.DuskOn else GlassPalette.WarmOn
    val fgMuted = fg.copy(alpha = 0.72f) // [fork/frost] ≥4.5:1 on the sheet
    var query by rememberSaveable { mutableStateOf("") }
    var archivedOpen by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<RecentItem?>(null) }
    val sections = remember(items, query) { RecentItems.sectionize(items, query) }
    // A search must be able to find an archived chat, so typing force-opens it.
    val showArchived = archivedOpen || query.isNotBlank()

    val hairline = GlassStyles.hairline(style)
    Box(
        modifier
            .width(minOf(LocalConfiguration.current.screenWidthDp * 0.84f, 340f).dp)
            .fillMaxHeight()
            .hazeEffect(state = haze, style = GlassStyles.sheet(style))
            .drawBehind {
                drawLine(hairline, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = 1f)
            },
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 12.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchField(query, { query = it }, fg, fgMuted, dark, Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = onNewChat) {
                    Icon(Icons.Outlined.EditNote, contentDescription = stringResource(R.string.glass_new_chat), tint = fg)
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.glass_recent),
                color = fgMuted,
                fontSize = MibotType.Section,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
            )
            if (sections.isEmpty()) {
                Text(
                    stringResource(if (query.isBlank()) R.string.glass_empty_recent else R.string.glass_no_results),
                    color = fgMuted,
                    fontSize = MibotType.Body,
                    modifier = Modifier.padding(start = 12.dp, top = 12.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                sections.forEach { section ->
                    if (section.bucket == RecentBucket.ARCHIVED) {
                        item(key = "h:archived") {
                            ArchiveHeader(
                                count = section.items.size,
                                open = showArchived,
                                fgMuted = fgMuted,
                                // Toggling while a query is active would fight the
                                // force-open rule; the header is then inert.
                                onToggle = { if (query.isBlank()) archivedOpen = !archivedOpen },
                            )
                        }
                        if (showArchived) {
                            items(section.items, key = { it.id }) { item ->
                                RecentRow(item, fg, fgMuted, dark, onClick = { onOpen(item) }, onDelete = { pendingDelete = item })
                            }
                        }
                    } else {
                        item(key = "h:${section.bucket}") { SectionHeader(sectionLabel(section.bucket), fgMuted) }
                        items(section.items, key = { it.id }) { item ->
                            RecentRow(item, fg, fgMuted, dark, onClick = { onOpen(item) }, onDelete = { pendingDelete = item })
                        }
                    }
                }
            }

            // [fork/settings-sidebar] Settings pinned to the sidebar bottom,
            // hairline-separated from the list above.
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(1.dp)
                    .background(GlassStyles.hairline(style)),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = null,
                    tint = fgMuted.copy(alpha = 0.72f),
                    modifier = Modifier.size(MibotIcon.Dense),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    t("设置", "Settings"),
                    color = fgMuted,
                    fontSize = MibotType.Body,
                )
            }
        }

        val target = pendingDelete
        if (target != null) {
            DeleteConfirmDialog(
                target = target,
                dark = dark,
                onDismiss = { pendingDelete = null },
                onConfirm = {
                    pendingDelete = null
                    onDelete(target)
                },
            )
        }
    }
}

@Composable
private fun sectionLabel(bucket: RecentBucket): String = when (bucket) {
    RecentBucket.PINNED -> t("置顶", "Pinned")
    RecentBucket.TODAY -> t("今天", "Today")
    RecentBucket.YESTERDAY -> t("昨天", "Yesterday")
    RecentBucket.THIS_WEEK -> t("过去 7 天", "Previous 7 days")
    RecentBucket.THIS_MONTH -> t("过去 30 天", "Previous 30 days")
    RecentBucket.ARCHIVED -> t("已归档", "Archived")
}

/** [fork/sidebar-sections] Small bucket caption above a group of rows. */
@Composable
private fun SectionHeader(label: String, fgMuted: Color) {
    Text(
        label,
        color = fgMuted.copy(alpha = 0.60f),
        fontSize = MibotType.Caption,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 2.dp),
    )
}

/**
 * [fork/sidebar-sections] The archive caption doubles as its toggle: tapping
 * expands or collapses the rows below it, and the journal-of-record stays out
 * of the way until asked for.
 */
@Composable
private fun ArchiveHeader(count: Int, open: Boolean, fgMuted: Color, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(start = 12.dp, end = 4.dp, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${t("已归档", "Archived")} · $count",
            color = fgMuted.copy(alpha = 0.60f),
            fontSize = MibotType.Caption,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (open) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
            contentDescription = if (open) t("收起", "Collapse") else t("展开", "Expand"),
            tint = fgMuted.copy(alpha = 0.60f),
            modifier = Modifier.size(MibotIcon.Small),
        )
    }
}

/** [fork/sidebar-sections] Delete confirmation — shared by chats and group chats. */
@Composable
private fun DeleteConfirmDialog(target: RecentItem, dark: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val title = if (target is RecentItem.Group) t("删除群聊？", "Delete group chat?") else t("删除对话？", "Delete chat?")
    val body = if (target is RecentItem.Group) {
        t("聊天记录会一起删除，Bot 本身不受影响。此操作无法撤销。", "Its messages are deleted too; the bots themselves are kept. This cannot be undone.")
    } else {
        t("这个对话的消息和文件会一并删除，无法恢复。", "This chat's messages and files are deleted with it. This cannot be undone.")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(t("删除", "Delete"), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("取消", "Cancel")) } },
        containerColor = if (dark) GlassPalette.DuskSurface else GlassPalette.WarmSurface,
        titleContentColor = if (dark) GlassPalette.DuskOn else GlassPalette.WarmOn,
        textContentColor = if (dark) GlassPalette.DuskOnMuted else GlassPalette.WarmOnMuted,
    )
}

@Composable
private fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    fg: Color,
    fgMuted: Color,
    dark: Boolean,
    modifier: Modifier,
) {
    Row(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.05f))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = fgMuted, modifier = Modifier.size(MibotIcon.Dense))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(stringResource(R.string.glass_search_hint), color = fgMuted, fontSize = MibotType.Body)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = fg, fontSize = MibotType.Body),
                cursorBrush = SolidColor(fg),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RecentRow(
    item: RecentItem,
    fg: Color,
    fgMuted: Color,
    dark: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.title ?: stringResource(R.string.glass_untitled),
            color = fg,
            fontSize = MibotType.Body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
        )
        if (item is RecentItem.Group) {
            Spacer(Modifier.width(8.dp))
            GroupBadge(fgMuted, dark)
            if (item.members.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                StackedAvatars(item.members, dark)
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = t("删除", "Delete"),
                tint = fgMuted.copy(alpha = 0.55f),
                modifier = Modifier.size(MibotIcon.Small),
            )
        }
    }
}

/** [fork/mibot-groups] Small 群聊 pill on group rows in the Recent list. */
@Composable
private fun GroupBadge(fgMuted: Color, dark: Boolean) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(t("群聊", "Group"), color = fgMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

private val AvatarColors = listOf(
    // [fork/mibot-v5] Muted identity tones (see MibotTokens).
    Color(0xFF66778A), Color(0xFFA9604F), Color(0xFF6C8A74), Color(0xFF7F7591), Color(0xFFA68B5B),
)

/** Up to three small overlapping bot avatars (initials), for group rows. */
@Composable
private fun StackedAvatars(members: List<String>, dark: Boolean) {
    val shown = members.take(3)
    if (shown.isEmpty()) return
    val ring = if (dark) Color(0xFF1B1D22) else Color.White
    Box(Modifier.width((18 + 11 * (shown.size - 1)).dp).height(18.dp)) {
        shown.forEachIndexed { i, name ->
            Box(
                Modifier
                    .offset(x = (11 * i).dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(AvatarColors[(name.hashCode() and 0x7fffffff) % AvatarColors.size])
                    .border(1.5.dp, ring, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(name.take(1).uppercase(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
