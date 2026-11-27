package com.openminis.app.mibot.dynamics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.openminis.app.ui.glass.LocalGlassBottomInset
import androidx.compose.foundation.layout.padding

/**
 * [fork/team-dynamics] Mibot host page for the team-dynamics plugin.
 * Owns a ViewModel, seeds the sample events once (there is no live DSH/agent
 * event producer wired into the phone yet), and renders the plugin screen.
 * Tapping a card with a goalId is handled by [onOpenGoal] when available.
 */
@Composable
fun TeamDynamicsPage(onOpenGoal: (String) -> Unit = {}) {
    val vm = remember { DynamicsPlugin.createViewModel() }
    val bottom = LocalGlassBottomInset.current
    LaunchedEffect(Unit) {
        val existing = DynamicsPlugin.exportJson(vm)
        if (existing == "[]" || existing.isBlank()) {
            DynamicsPlugin.pushAllJson(vm, DynamicsPlugin.sampleJson())
        }
    }
    Box(Modifier.fillMaxSize().padding(bottom = bottom)) {
        DynamicsPlugin.Screen(vm) { activity ->
            activity.goalId?.let(onOpenGoal)
        }
    }
}
