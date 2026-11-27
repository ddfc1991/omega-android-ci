package com.openminis.app.ui.muse

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.openminis.app.ui.glass.GlassPrefs

/**
 * [fork/muse] Muse design language (Mines build-36 spec) switch.
 *
 * Default ON for the Muse build line (applicationId com.dl1991.muse). When
 * off the stock glass shell renders, so the two designs can be A/B'ed
 * in place without a rebuild. The user-facing toggle lives in the Muse
 * feed header menu.
 */
object MusePrefs {
    private const val NAME = "muse_ui_prefs"
    private const val K_ENABLED = "muse_enabled"
    private const val K_LIGHT = "muse_light"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(K_ENABLED, true)

    /** [01 首页·浅色 / 02 首页·深色] — Muse runs its own theme toggle,
     *  independent of the system app theme, matching the spec's dual
     *  light/dark sheets. */
    fun isLight(context: Context): Boolean =
        prefs(context).getBoolean(K_LIGHT, false)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(K_ENABLED, value).apply()
    }

    fun setLight(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(K_LIGHT, value).apply()
    }
}

/** Flow-backed state so UI reacts when the toggle flips (dev server / future
 *  minis-config). Kept tiny on purpose. */
@Composable
fun rememberMuseState(): MuseState = remember {
    val context = androidx.compose.ui.platform.LocalContext.current
    MuseState(
        enabled = mutableStateOf(MusePrefs.isEnabled(context)),
        light = mutableStateOf(MusePrefs.isLight(context)),
    )
}

class MuseState(
    val enabled: State<Boolean>,
    val light: State<Boolean>,
)
