/*
 * [fork/mika-desk] Draggable PiP preview of the agent virtual display.
 * Uses Mika's PipPhysics to snap a thrown PiP to the nearest corner.
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toDp
import com.openminis.app.mibot.design.MibotTokens
import com.openminis.app.mibot.phone.VdController
import kotlin.math.roundToInt

private val PipShape = RoundedCornerShape(14.dp)

/** Draggable PiP over the hidden display; drop on the bottom pill to dock. */
@Composable
fun VdDraggablePip(onOpen: () -> Unit, onDock: () -> Unit, modifier: Modifier = Modifier) {
    val vd by VdController.state.collectAsState()
    if (vd.mode != VdController.Mode.HELPER) return
    val source by VdController.previewSource.collectAsState()
    val frame by source.collectAsState()

    val density = LocalDensity.current
    val pipW = with(density) { 116.dp.toPx() }
    val aspect = if (vd.height > 0) vd.width.toFloat() / vd.height.toFloat() else 1.8f
    val pipH = pipW / aspect.coerceIn(0.5f, 3f)

    var hostW by remember { mutableStateOf(0) }
    var hostH by remember { mutableStateOf(0) }
    var corner by remember { mutableStateOf(PipPhysics.Corner.TopEnd) }
    val xAnim = remember { Animatable(0f) }
    val yAnim = remember { Animatable(0f) }

    val margin = with(density) { 12.dp.toPx() }
    val topInset = with(density) { 56.dp.toPx() }
    val bottomInset = with(density) { 72.dp.toPx() }

    // Settle to the current corner whenever it (or the host size) changes.
    LaunchedEffect(hostW, hostH, corner) {
        if (hostW <= 0 || hostH <= 0) return@LaunchedEffect
        val b = PipPhysics.Bounds(
            width = hostW.toFloat(), height = hostH.toFloat(),
            pipWidth = pipW, pipHeight = pipH,
            margin = margin, top = topInset, bottom = bottomInset,
        )
        val (tx, ty) = b.position(corner)
        xAnim.animateTo(tx, tween(300))
        yAnim.animateTo(ty, tween(300))
    }

    Box(modifier.fillMaxSize().onSizeChanged { hostW = it.width; hostH = it.height }) {
        // 流云体 dock target.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MibotTokens.accent().copy(alpha = 0.16f))
                .border(1.dp, MibotTokens.accent().copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 7.dp),
        ) { Text("流云体 · Dock", color = MibotTokens.accent(), fontSize = 12.sp) }

        Box(
            Modifier
                .offset { IntOffset(xAnim.value.roundToInt(), yAnim.value.roundToInt()) }
                .size(
                    width = with(density) { pipW.toInt().toDp() },
                    height = with(density) { pipH.toInt().toDp() },
                )
                .clip(PipShape)
                .background(Color.Black)
                .border(1.5.dp, MibotTokens.accent(), PipShape)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { },
                        onDrag = { change, amount ->
                            change.consume()
                            xAnim.snapTo(xAnim.value + amount.x)
                            yAnim.snapTo(yAnim.value + amount.y)
                        },
                        onDragEnd = {
                            val b = PipPhysics.Bounds(
                                width = hostW.toFloat(), height = hostH.toFloat(),
                                pipWidth = pipW, pipHeight = pipH,
                                margin = margin, top = topInset, bottom = bottomInset,
                            )
                            if (hostW > 0) {
                                val px = xAnim.value + pipW / 2f
                                val py = yAnim.value + pipH
                                if (py >= hostH - with(density) { 60.dp.toPx() } &&
                                    kotlin.math.abs(px - hostW / 2f) < hostW * 0.28f
                                ) onDock()
                                corner = PipPhysics.snapCorner(xAnim.value, yAnim.value, 0f, 0f, b)
                            }
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
