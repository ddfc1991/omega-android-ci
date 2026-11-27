/*
 * [fork/mika-desk] Draggable PiP preview of the agent virtual display.
 * Drag physics (throw-to-corner + critically-damped spring) come from Mika's
 * PipPhysics (Apache-2.0, adapted); dropping on the bottom pill "docks" the
 * preview into the capsule overlay (流云体/灵动岛).
 */
package com.openminis.app.mibot.desk

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toDp
import com.openminis.app.mibot.design.MibotTokens
import com.openminis.app.mibot.phone.VdController
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val PipShape = RoundedCornerShape(14.dp)

/**
 * A draggable, throw-to-corner PiP showing the agent display's live frames.
 * [onOpen] expands back to the full VdScreen; [onDock] drops the preview into
 * the capsule (流云体) — the host decides what that means (usually: keep a
 * small capsule running while the PiP hides).
 */
@Composable
fun VdDraggablePip(
    onOpen: () -> Unit,
    onDock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vd by VdController.state.collectAsState()
    if (vd.mode != VdController.Mode.HELPER) return
    val source by VdController.previewSource.collectAsState()
    val frame by source.collectAsState()

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // Host size (the PiP lives inside a fillMaxSize box).
    var hostW by remember { mutableStateOf(0) }
    var hostH by remember { mutableStateOf(0) }

    // Pip size from the display aspect (Mika PipSizing.fit, simplified).
    val pipW = with(density) { 116.dp.toPx() }
    val aspect = if (vd.height > 0) vd.width.toFloat() / vd.height else 1.8f
    val pipH = (pipW / aspect.coerceIn(0.5f, 3f))

    // Corner the PiP rests at.
    var corner by remember { mutableStateOf(PipPhysics.Corner.TopEnd) }
    val xAnim = remember { Animatable(0f) }
    val yAnim = remember { Animatable(0f) }
    var inited by remember { mutableStateOf(false) }

    fun bounds(): PipPhysics.Bounds = PipPhysics.Bounds(
        width = hostW.toFloat(), height = hostH.toFloat(),
        pipWidth = pipW, pipHeight = pipH,
        margin = with(density) { 12.dp.toPx() },
        top = with(density) { 56.dp.toPx() },
        bottom = with(density) { 72.dp.toPx() },
    )

    // On host size or corner change, spring the PiP to that corner.
    LaunchedEffect(hostW, hostH, corner) {
        if (hostW <= 0 || hostH <= 0) return@LaunchedEffect
        val (tx, ty) = bounds().position(corner)
        xAnim.animateTo(tx, tween(320))
        yAnim.animateTo(ty, tween(320))
        inited = true
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { hostW = it.width; hostH = it.height },
    ) {
        // Dock target: a pill at bottom-center ("流云体"). Highlights implicitly
        // by the PiP shrinking when dropped near it.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MibotTokens.accent().copy(alpha = 0.16f))
                .border(1.dp, MibotTokens.accent().copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 7.dp),
        ) {
            Text("流云体 · Dock", color = MibotTokens.accent(), fontSize = 12.sp)
        }

        if (inited && hostW > 0) {
            val offX = xAnim.value
            val offY = yAnim.value
            Box(
                Modifier
                    .offset { IntOffset(offX.roundToInt(), offY.roundToInt()) }
                    .size(width = with(density) { pipW.toDp() }, height = with(density) { pipH.toDp() })
                    .clip(PipShape)
                    .background(Color.Black)
                    .border(1.5.dp, MibotTokens.accent(), PipShape)
                    .pointerInput(corner) {
                        detectDragGestures(
                            onDragStart = { /* pause spring by taking over anim */ },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    xAnim.snapTo(xAnim.value + dragAmount.x)
                                    yAnim.snapTo(yAnim.value + dragAmount.y)
                                }
                            },
                            onDragEnd = {
                                val b = bounds()
                                // Drop on the dock pill (bottom-center band)?
                                val px = xAnim.value + pipW / 2
                                val py = yAnim.value + pipH
                                val dockCenterX = hostW / 2f
                                val dockTop = hostH - with(density) { 60.dp.toPx() }
                                if (py >= dockTop && kotlin.math.abs(px - dockCenterX) < hostW * 0.28f) {
                                    onDock()
                                }
                                // Mika physics: project the throw, snap to nearest corner.
                                corner = PipPhysics.snapCorner(
                                    xAnim.value, yAnim.value, 0f, 0f, b,
                                )
                            },
                        )
                    },
            ) {
                frame?.takeIf { !it.isRecycled }?.let {
                    Image(it.asImageBitmap(), contentDescription = null, Modifier.fillMaxSize())
                }
                Text(
                    "后台",
                    color = Color.White,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 3.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MibotTokens.accent())
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
    }
}
