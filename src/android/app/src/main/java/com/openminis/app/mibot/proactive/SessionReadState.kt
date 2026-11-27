package com.openminis.app.mibot.proactive

import android.content.Context

/**
 * [fork/proactive-inbox] Per-session PROACTIVE-unread state, kept in plain
 * SharedPreferences rather than a Room column. Read state is not core chat
 * data — losing it merely drops an unread dot — so it does not justify a
 * schema migration (the DB is at version 14 and Room validates the live
 * schema on open, so every column is a verified migration pair).
 *
 * The model is an EXPLICIT unread marker, not a derived `updatedAt >
 * lastReadAt` guess — that derived form wrongly flags every pre-existing
 * session as unread on first launch (their `lastReadAt` is 0) and cannot
 * tell a user-initiated reply apart from a proactive one.
 *
 *  - [markUnread]: a PROACTIVE event produced content the user has not seen —
 *    a background/scheduled agent finished, an environment signal fired, a
 *    relayed notification arrived. This is the "Mibot came to you" moment.
 *  - [markRead]: the session became visible (ChatScreen entered or left it).
 *  - [hasUnread]: the proactive marker is newer than the last open.
 *
 * A chat the user just answered in stays read because nothing calls
 * [markUnread] for a user-initiated turn — only proactive sources do.
 */
object SessionReadState {
    private const val PREFS = "mibot_session_read_state"
    private const val KEY_UNREAD_PREFIX = "unread:"
    private const val KEY_READ_PREFIX = "read:"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** A proactive source delivered content into [sessionId]. */
    fun markUnread(ctx: Context, sessionId: String, at: Long = System.currentTimeMillis()) {
        if (sessionId.isBlank()) return
        prefs(ctx).edit().putLong(KEY_UNREAD_PREFIX + sessionId, at).apply()
    }

    /** The user looked at [sessionId] (entering or leaving its screen). */
    fun markRead(ctx: Context, sessionId: String) {
        if (sessionId.isBlank()) return
        prefs(ctx).edit().putLong(KEY_READ_PREFIX + sessionId, System.currentTimeMillis()).apply()
    }

    fun hasUnread(ctx: Context, sessionId: String): Boolean {
        if (sessionId.isBlank()) return false
        val p = prefs(ctx)
        val unread = p.getLong(KEY_UNREAD_PREFIX + sessionId, 0L)
        if (unread == 0L) return false
        val read = p.getLong(KEY_READ_PREFIX + sessionId, 0L)
        return unread > read
    }

    /** Clear the proactive marker without touching read state (e.g. on delete). */
    fun clear(ctx: Context, sessionId: String) {
        prefs(ctx).edit()
            .remove(KEY_UNREAD_PREFIX + sessionId)
            .remove(KEY_READ_PREFIX + sessionId)
            .apply()
    }
}
