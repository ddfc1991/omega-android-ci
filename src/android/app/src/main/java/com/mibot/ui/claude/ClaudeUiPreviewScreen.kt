package com.mibot.ui.claude

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mibot.chat.ConversationSummary
import com.mibot.ui.hq.HqMoreEntry
import androidx.compose.runtime.collectAsState
import com.openminis.app.data.db.ChatDao

/**
 * Claude 主壳 —— 用逆向 Claude 的整套壳承载 Omega 的真实数据。
 *
 * 这是「Claude 外观 + Omega 功能」的核心：Claude 原生骨架
 * （纯黑底 / 顶栏 ≡+asterisk / 会话侧栏 / 白胶囊输入栏）+ 真实会话 + 真实消息。
 *
 * @param sessions 真实会话列表（Omega chat DB）
 * @param dao 聊天 DAO（按选中会话读消息；null 时用占位数据）
 * @param onOpenSession 点击会话（外层可接管，如跳转）
 * @param onMoreEntry 更多入口（设置/模型/通话等 Omega 功能）
 */
@Composable
fun ClaudeUiPreviewScreen(
    sessions: List<ConversationSummary> = previewSessions,
    dao: ChatDao? = null,
    onOpenSession: (String) -> Unit = {},
    onMoreEntry: (HqMoreEntry) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selectedId by remember { mutableStateOf<String?>(sessions.firstOrNull()?.id) }

    // 选中会话 → 真实消息；无 dao 或未选中 → 占位
    val messages: List<ClaudeMessage> = if (dao != null && selectedId != null) {
        val rows by dao.observeMessages(selectedId!!).collectAsState(initial = emptyList())
        rows.map { it.toClaudeMessage() }
    } else {
        previewMessages
    }

    ClaudeShell(
        chatContent = {
            ClaudeChatScreen(
                messages = messages,
                emptyPrompt = "What would you like to work on?",
                suggestions = listOf("帮我看看这段日志", "重构这个函数", "解释这段代码"),
                onSuggestion = {},
                onSend = {},
                modifier = Modifier,
            )
        },
        sessions = sessions,
        onOpenSession = { id ->
            selectedId = id
            onOpenSession(id)
        },
        onDeleteSession = {},
        showSidebar = true,
        modifier = modifier,
    )
}

/** MessageEntity → ClaudeMessage（role + partsJson 文本）。 */
private fun com.openminis.app.data.db.MessageEntity.toClaudeMessage(): ClaudeMessage =
    ClaudeMessage(
        id = id,
        text = partsToText(partsJson),
        isUser = role.equals("user", ignoreCase = true),
    )

/** partsJson（JSONArray of parts）→ 拼接 text 部分。 */
private fun partsToText(partsJson: String): String {
    val arr = runCatching { org.json.JSONArray(partsJson) }.getOrNull() ?: return partsJson
    val sb = StringBuilder()
    for (i in 0 until arr.length()) {
        val p = arr.optJSONObject(i) ?: continue
        if (p.optString("type") == "text") {
            val v = p.optJSONObject("value")
            sb.append(v?.optString("text").orEmpty())
        }
    }
    return sb.toString().ifBlank { partsJson.take(200) }
}

// ---- 示例占位（视觉对齐 Claude 界面;真实会话由外层注入） ----
private val nowMs = System.currentTimeMillis()
private val previewSessions = listOf(
    ConversationSummary(id = "c1", title = "Claude app 逆向", updatedAt = nowMs - 30 * 60_000L),
    ConversationSummary(id = "c2", title = "设计系统", updatedAt = nowMs - 2 * 3_600_000L),
    ConversationSummary(id = "c3", title = "登录流程", updatedAt = nowMs - 6 * 3_600_000L),
    ConversationSummary(id = "c4", title = "供应商配置", updatedAt = nowMs - 24 * 3_600_000L),
)
private val previewMessages = listOf(
    ClaudeMessage(id = "m1", text = "帮我看下这个界面是什么风格", isUser = true),
    ClaudeMessage(id = "m2", text = "这是 Claude 的深色主界面:纯黑底、橙色 asterisk 强调、Claude Sans 字体。", isUser = false),
)
