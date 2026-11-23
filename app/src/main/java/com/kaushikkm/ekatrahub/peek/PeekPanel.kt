package com.kaushikkm.ekatrahub.peek

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kaushikkm.ekatrahub.ui.timeline.LocalPanelDrag
import com.kaushikkm.ekatrahub.ui.timeline.PanelDrag
import com.kaushikkm.ekatrahub.ui.timeline.TimelineScreen

/**
 * The Hub that slides in over other apps.
 *
 * Smoothness: [progress] is read only inside graphicsLayer {} blocks. That means following your
 * finger just moves/fades already-drawn layers on the GPU every frame - nothing is recomposed
 * or re-measured while you drag.
 *
 * Swiping the panel back towards its edge (anywhere: header, app rail or a notification) slides
 * it away, following your finger - like pushing it back where it came from.
 */
@Composable
internal fun PeekPanel(
    progress: () -> Float,
    edge: PeekEdge,
    panelWidth: Dp,
    onClose: () -> Unit,
    onPanelDrag: (Float) -> Unit,
    onPanelDragEnd: (Float) -> Unit,
    onOpenApp: () -> Unit,
) {
    val fromLeft = edge == PeekEdge.LEFT
    val closeDirection = if (fromLeft) -1f else 1f
    val panelDrag = remember(closeDirection) { PanelDrag(closeDirection, onPanelDrag, onPanelDragEnd) }

    Box(Modifier.fillMaxSize()) {
        // Dim the app underneath; tapping it closes the Hub.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress() }
                .background(Color.Black.copy(alpha = 0.45f))
                .pointerInput(Unit) { detectTapGestures { onClose() } },
        )
        Surface(
            modifier = Modifier
                .align(if (fromLeft) Alignment.CenterStart else Alignment.CenterEnd)
                .fillMaxHeight()
                .width(panelWidth)
                .graphicsLayer {
                    val hidden = (1f - progress()) * size.width
                    translationX = if (fromLeft) -hidden else hidden
                }
                // Drags that start outside a notification row (header, app rail, empty space).
                .draggable(
                    state = rememberDraggableState { delta -> onPanelDrag(delta * closeDirection) },
                    orientation = Orientation.Horizontal,
                    onDragStopped = { velocity -> onPanelDragEnd(velocity * closeDirection) },
                ),
            shape = if (fromLeft) {
                RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
            } else {
                RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
            },
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 12.dp,
        ) {
            CompositionLocalProvider(LocalPanelDrag provides panelDrag) {
                TimelineScreen(inOverlay = true, onClose = onClose, onLogoClick = onOpenApp)
            }
        }
    }
}
