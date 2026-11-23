package com.kaushikkm.ekatrahub.ui.timeline

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Lets swipes inside the timeline move the Peek panel itself.
 * Only provided when the timeline is shown inside Peek; null inside the app.
 *
 * @param closeDirection screen direction that pushes the panel back to its edge:
 *   -1 = leftwards (handle on the left edge), +1 = rightwards (handle on the right edge).
 * @param onDrag pixels moved towards closing (negative = back towards open).
 * @param onDragEnd finger speed towards closing, in px/s, when the finger lifts.
 */
class PanelDrag(
    val closeDirection: Float,
    val onDrag: (Float) -> Unit,
    val onDragEnd: (Float) -> Unit,
)

val LocalPanelDrag = staticCompositionLocalOf<PanelDrag?> { null }
