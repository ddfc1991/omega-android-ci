package com.openminis.app.ui.glass

import com.openminis.app.data.db.ChatSessionEntity
import java.util.Calendar
import java.util.TimeZone

/** One row of the sidebar's single mixed "Recent" list. */
sealed class RecentItem {
    abstract val id: String
    abstract val title: String?
    abstract val updatedAt: Long

    data class Chat(
        override val id: String,
        override val title: String?,
        override val updatedAt: Long,
        val pinned: Boolean = false,
    ) : RecentItem()

    /**
     * A bot group chat ([com.openminis.app.mibot.groups.GroupChatStore]),
     * rendered inline with chats and opened on the group chat route.
     */
    data class Group(
        override val id: String,
        override val title: String?,
        override val updatedAt: Long,
        /** Display names of member bots; first letters become stacked avatars. */
        val members: List<String>,
    ) : RecentItem()
}

/**
 * [fork/sidebar-sections] Where a row lands once the recent list is sectioned.
 * Declaration order is display order.
 */
enum class RecentBucket { PINNED, TODAY, YESTERDAY, THIS_WEEK, THIS_MONTH, ARCHIVED }

/** One rendered group of rows: Pinned, a local-calendar date bucket, or the archive. */
data class SidebarSection(
    val bucket: RecentBucket,
    val items: List<RecentItem>,
)

object RecentItems {
    /**
     * [fork/sidebar-sections] Rows untouched for this many days leave the
     * recent list for the collapsed "Archived" section. Derived at render
     * time — no DB column, nothing deleted — so opening the chat (which bumps
     * `updatedAt`) or pinning it brings the row straight back.
     */
    const val ARCHIVE_AFTER_DAYS = 30

    fun fromSessions(sessions: List<ChatSessionEntity>): List<RecentItem> =
        sessions.asSequence()
            .filter { !it.isChild }
            .map { RecentItem.Chat(it.id, it.title?.takeIf { t -> t.isNotBlank() }, it.updatedAt, it.pinnedAt != null) }
            .toList()

    /** Pinned first, then newest first; [query] matches titles case-insensitively. */
    fun merge(chats: List<RecentItem>, groups: List<RecentItem>, query: String): List<RecentItem> {
        val q = query.trim()
        return (chats + groups)
            .filter { q.isEmpty() || (it.title ?: "").contains(q, ignoreCase = true) }
            .sortedWith(compareByDescending<RecentItem> { (it as? RecentItem.Chat)?.pinned == true }.thenByDescending { it.updatedAt })
    }

    /**
     * [fork/sidebar-sections] Auto-split the mixed list into time sections:
     *
     *     Pinned → Today → Yesterday → Previous 7 days → Previous 30 days → Archived
     *
     * Boundaries are local CALENDAR days, not rolling 24 h windows, so a chat
     * sent at 23:50 is under "Yesterday" at 00:10 — which is what the label
     * promises. Empty sections are omitted, so the sidebar shows only what it
     * has.
     *
     * Archive is a VIEW, not a write: rows older than [ARCHIVE_AFTER_DAYS]
     * calendar days are still returned (last section) and the sidebar renders
     * them collapsed. Pinned rows never archive — a pin is an explicit "keep
     * this here". Search bypasses the split in spirit: results keep their
     * buckets, and an archived hit stays visible because the sidebar expands
     * the archive while a query is active.
     */
    fun sectionize(
        items: List<RecentItem>,
        query: String = "",
        now: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.getDefault(),
    ): List<SidebarSection> {
        val q = query.trim()
        val startToday = startOfDay(now, zone)
        val startYesterday = dayStart(startToday, 1, zone)
        val startWeek = dayStart(startToday, 7, zone)
        val startArchive = dayStart(startToday, ARCHIVE_AFTER_DAYS, zone)

        val pinned = ArrayList<RecentItem>()
        val today = ArrayList<RecentItem>()
        val yesterday = ArrayList<RecentItem>()
        val week = ArrayList<RecentItem>()
        val month = ArrayList<RecentItem>()
        val archived = ArrayList<RecentItem>()

        for (item in items) {
            if (q.isNotEmpty() && !(item.title ?: "").contains(q, ignoreCase = true)) continue
            if ((item as? RecentItem.Chat)?.pinned == true) {
                pinned += item
                continue
            }
            when {
                item.updatedAt >= startToday -> today += item
                item.updatedAt >= startYesterday -> yesterday += item
                item.updatedAt >= startWeek -> week += item
                item.updatedAt >= startArchive -> month += item
                else -> archived += item
            }
        }

        // Newest first inside every bucket; sections keep their declaration order.
        return buildList {
            fun put(bucket: RecentBucket, rows: List<RecentItem>) {
                if (rows.isNotEmpty()) add(SidebarSection(bucket, rows.sortedByDescending { it.updatedAt }))
            }
            put(RecentBucket.PINNED, pinned)
            put(RecentBucket.TODAY, today)
            put(RecentBucket.YESTERDAY, yesterday)
            put(RecentBucket.THIS_WEEK, week)
            put(RecentBucket.THIS_MONTH, month)
            put(RecentBucket.ARCHIVED, archived)
        }
    }

    /** Local midnight at the start of [millis]'s calendar day. */
    fun startOfDay(millis: Long, zone: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /**
     * Midnight [daysAgo] calendar days before [startOfToday]. Re-zeroes the
     * time fields after the shift so DST transitions (23 h / 25 h days) still
     * land on a real local midnight.
     */
    private fun dayStart(startOfToday: Long, daysAgo: Int, zone: TimeZone): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = startOfToday
            add(Calendar.DAY_OF_YEAR, -daysAgo)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}

/**
 * Sample rows for screenshot runs. Only ever enabled on debug builds, via the
 * `glass_demo` launch extra the CI smoke test passes (see MainActivity).
 */
object GlassDemo {
    @Volatile var enabled: Boolean = false

    fun items(now: Long = System.currentTimeMillis()): List<RecentItem> = listOf(
        RecentItem.Chat("demo-1", "周末杭州两日行程", now - 5 * 60_000L),
        RecentItem.Group("demo-g1", "产品评审小组", now - 22 * 60_000L, listOf("Planner", "Critic", "Writer")),
        RecentItem.Chat("demo-2", "Kotlin 协程取消的坑", now - 2 * 3_600_000L),
        RecentItem.Group("demo-g2", "小说写作工作室", now - 5 * 3_600_000L, listOf("Muse", "Editor")),
        RecentItem.Chat("demo-3", "读书笔记：《置身事内》", now - 26 * 3_600_000L),
        RecentItem.Chat("demo-4", "家里 NAS 备份方案", now - 50 * 3_600_000L),
        // Older than ARCHIVE_AFTER_DAYS so the smoke run exercises the archive.
        RecentItem.Chat("demo-5", "去年的旅行计划", now - 45L * 24 * 3_600_000L),
    )
}
