package com.openminis.app.ui.muse

import androidx.compose.ui.graphics.Color

/**
 * [fork/muse] Muse design language tokens — Mines build-36 spec, taken from
 * the design sheet's colour bar (exact values):
 *
 *   深底 #050505   弹层 #1F1F1F   凸起 #343638
 *   正文 #F5F5F5   次文 #A4A6A8
 *   主蓝 #007FFD   浅色主蓝 #0064D4   危险 #C8283A
 *   浅底 #FAFAFA   分组 #F3F4F6   AI气泡 #E9EAEC   我的气泡 #CBEEFE
 *
 * Semantic extras read off the screens: 告警橙 #CB823A, 成功绿 #36B34B.
 */
object MuseColors {
    // Dark theme (02/03/04/05/06/07 screens)
    val DarkBase = Color(0xFF050505)
    val DarkSheet = Color(0xFF1F1F1F)
    val DarkRaised = Color(0xFF343638)
    val DarkHairline = Color(0x14FFFFFF) // 8% white hairline
    val DarkChip = Color(0xFF26262A)
    val DarkInput = Color(0xFF161619)

    // Light theme (01/08/09/10 screens)
    val LightBase = Color(0xFFFAFAFA)
    val LightGroup = Color(0xFFF3F4F6)
    val LightCard = Color(0xFFFFFFFF)
    val LightHairline = Color(0x14000000)
    val LightChip = Color(0xFFEDEEF0)
    val LightInput = Color(0xFFEFF0F2)

    // Text
    val InkDark = Color(0xFFF5F5F5) // 正文 on dark
    val InkMutedDark = Color(0xFFA4A6A8) // 次文 on dark
    val InkLight = Color(0xFF17181A) // 正文 on light
    val InkMutedLight = Color(0xFF6B6E73) // 次文 on light

    // Accent
    val Blue = Color(0xFF007FFD) // 主蓝 (dark contexts)
    val BlueLight = Color(0xFF0064D4) // 浅色主蓝 (light contexts)
    val BlueDim = Color(0x33007FFD) // 8% blue tint fill
    val OnBlue = Color(0xFFFFFFFF)

    // Bubbles
    val MyBubbleLight = Color(0xFFCBEEFE) // 我的气泡 (light theme)
    val MyBubbleDark = Color(0xFF007FFD) // my bubble dark theme = blue
    val AiBubbleDark = Color(0xFFE9EAEC) // AI 气泡 (shown light on white? spec chip)
    val AiCardDark = Color(0xFF161619) // AI reply card on dark feed/chat
    val AiCardLight = Color(0xFFFFFFFF)
    val OnMyBubbleLight = Color(0xFF0B2733)
    val OnMyBubbleDark = Color(0xFFFFFFFF)

    // Semantic
    val Danger = Color(0xFFC8283A) // 危险
    val Warn = Color(0xFFCB823A) // 告警橙 (warning cards)
    val Success = Color(0xFF36B34B) // 成功绿
    val WarnDim = Color(0x1FCB823A)
    val SuccessDim = Color(0x1F36B34B)
    val DangerDim = Color(0x1FC8283A)
}

/** Resolved token set for the active Muse theme. */
data class MuseTheme(
    val dark: Boolean,
    val base: Color,
    val sheet: Color,
    val raised: Color,
    val hairline: Color,
    val chip: Color,
    val input: Color,
    val ink: Color,
    val inkMuted: Color,
    val accent: Color,
    val myBubble: Color,
    val onMyBubble: Color,
    val aiCard: Color,
) {
    companion object {
        fun resolve(dark: Boolean) = if (dark) {
            MuseTheme(
                dark = true,
                base = MuseColors.DarkBase,
                sheet = MuseColors.DarkSheet,
                raised = MuseColors.DarkRaised,
                hairline = MuseColors.DarkHairline,
                chip = MuseColors.DarkChip,
                input = MuseColors.DarkInput,
                ink = MuseColors.InkDark,
                inkMuted = MuseColors.InkMutedDark,
                accent = MuseColors.Blue,
                myBubble = MuseColors.MyBubbleDark,
                onMyBubble = MuseColors.OnMyBubbleDark,
                aiCard = MuseColors.AiCardDark,
            )
        } else {
            MuseTheme(
                dark = false,
                base = MuseColors.LightBase,
                sheet = MuseColors.LightCard,
                raised = MuseColors.LightGroup,
                hairline = MuseColors.LightHairline,
                chip = MuseColors.LightChip,
                input = MuseColors.LightInput,
                ink = MuseColors.InkLight,
                inkMuted = MuseColors.InkMutedLight,
                accent = MuseColors.BlueLight,
                myBubble = MuseColors.MyBubbleLight,
                onMyBubble = MuseColors.OnMyBubbleLight,
                aiCard = MuseColors.AiCardLight,
            )
        }
    }
}
