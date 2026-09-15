package com.dhikr.app.feature.counter

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Vector paths ported from the prototype's inline SVGs
// (design/Dhikr Android App.dc.html lines 40, 47, 92, 95). All icons share the
// prototype's stroke treatment: 24x24 viewport, 2.75 stroke width, round caps
// and joins, no fill. Elliptical arcs from the SVG source ("a9 9 0 1 0 ...") are
// converted to cubic beziers, since ImageVector has no arc-to helper. Each arc's
// centre and sweep are solved from the SVG endpoint parameterisation, then split
// into equal sub-90-degree segments using the exact circular-arc control-point
// formula (k = 4/3 * tan(step/4)) so every on-curve point lies on the true
// circle. Both the sweep DIRECTION and the terminal point matter visually — see
// the per-icon notes below.
private const val STROKE_WIDTH = 2.75f

fun backChevronIcon(): ImageVector = ImageVector.Builder(
    name = "BackChevron",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    // SVG: M15 18l-6-6 6-6
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(15f, 18f)
        lineTo(9f, 12f)
        lineTo(15f, 6f)
    }
}.build()

/** Mirror of [backChevronIcon] — points right instead of left. Used for the
 * "next tasbih" control row button; [backChevronIcon] itself doubles as the
 * "previous tasbih" icon since the shapes are identical. */
fun forwardChevronIcon(): ImageVector = ImageVector.Builder(
    name = "ForwardChevron",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    // SVG: M9 6l6 6-6 6
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(9f, 6f)
        lineTo(15f, 12f)
        lineTo(9f, 18f)
    }
}.build()

/** Two vertical bars — standard pause glyph, shown while the session is running. */
fun pauseIcon(): ImageVector = ImageVector.Builder(
    name = "Pause",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(8f, 5f)
        lineTo(8f, 19f)
    }
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(16f, 5f)
        lineTo(16f, 19f)
    }
}.build()

/** Right-pointing triangle — standard play glyph, shown while paused (tap to resume). */
fun playIcon(): ImageVector = ImageVector.Builder(
    name = "Play",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color.Black),
        stroke = null,
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(7f, 4.5f)
        lineTo(19f, 12f)
        lineTo(7f, 19.5f)
        close()
    }
}.build()

/** Sheet-of-notes glyph: a document outline with two ruled lines. */
fun noteIcon(): ImageVector = ImageVector.Builder(
    name = "Note",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(6f, 3.5f)
        lineTo(18f, 3.5f)
        curveTo(18.83f, 3.5f, 19.5f, 4.17f, 19.5f, 5f)
        lineTo(19.5f, 19f)
        curveTo(19.5f, 19.83f, 18.83f, 20.5f, 18f, 20.5f)
        lineTo(6f, 20.5f)
        curveTo(5.17f, 20.5f, 4.5f, 19.83f, 4.5f, 19f)
        lineTo(4.5f, 5f)
        curveTo(4.5f, 4.17f, 5.17f, 3.5f, 6f, 3.5f)
        close()
    }
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(8f, 9f)
        lineTo(16f, 9f)
    }
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(8f, 14f)
        lineTo(14f, 14f)
    }
}.build()

fun undoIcon(): ImageVector = ImageVector.Builder(
    name = "Undo",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    // SVG: M3 7v6h6
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(3f, 7f)
        lineTo(3f, 13f)
        lineTo(9f, 13f)
    }
    // SVG: M3 13a9 9 0 1 0 3-7.7
    // Solving the SVG arc endpoint parameterisation for r=9 through (3,13) and
    // (6,5.3) gives centre (11.95, 12.05) — NOT (12,13) — and sweep=0 means a
    // counter-clockwise on-screen sweep of -305.3 degrees. The arc must end
    // ABOVE its start, so the gap in the spiral meets the arrowhead chevron at
    // upper-left; traversing the other way mirrors the icon vertically and
    // leaves the arrowhead detached.
    // Split into 4 equal sub-90-degree segments, each an exact circular bezier
    // (k = 4/3 * tan(step/4)), so every point lies on the true radius-9 circle.
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(3.00f, 12.99f)
        curveTo(3.43f, 17.12f, 6.63f, 20.42f, 10.75f, 20.97f)
        curveTo(14.86f, 21.53f, 18.82f, 19.20f, 20.33f, 15.33f)
        curveTo(21.84f, 11.46f, 20.51f, 7.07f, 17.12f, 4.68f)
        curveTo(13.72f, 2.30f, 9.13f, 2.55f, 6.01f, 5.29f)
    }
}.build()

// Trash-can glyph, not a mirrored undo arrow — the two need to read as
// different actions (undo one tap vs. wipe the whole count) at a glance.
fun resetIcon(): ImageVector = ImageVector.Builder(
    name = "Reset",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    // SVG: M4 7h16
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(4f, 7f)
        lineTo(20f, 7f)
    }
    // SVG: M9 7V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(9f, 7f)
        lineTo(9f, 5f)
        curveTo(9f, 3.9f, 9.9f, 3f, 11f, 3f)
        lineTo(13f, 3f)
        curveTo(14.1f, 3f, 15f, 3.9f, 15f, 5f)
        lineTo(15f, 7f)
    }
    // SVG: M6 7l1 13a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-13
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(6f, 7f)
        lineTo(7f, 20f)
        curveTo(7f, 21.1f, 7.9f, 22f, 9f, 22f)
        lineTo(15f, 22f)
        curveTo(16.1f, 22f, 17f, 21.1f, 17f, 20f)
        lineTo(18f, 7f)
    }
    // SVG: M10 11v6
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(10f, 11f)
        lineTo(10f, 17f)
    }
    // SVG: M14 11v6
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(14f, 11f)
        lineTo(14f, 17f)
    }
}.build()

fun checkIcon(): ImageVector = ImageVector.Builder(
    name = "Check",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    // Simple checkmark: (5,13) -> (10,18) -> (19,6). Straight lines only, no
    // curves needed for this shape.
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(5f, 13f)
        lineTo(10f, 18f)
        lineTo(19f, 6f)
    }
}.build()

fun lockIcon(locked: Boolean): ImageVector = ImageVector.Builder(
    name = if (locked) "LockClosed" else "LockOpen",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    // SVG: <rect x="4" y="11" width="16" height="10" rx="3" />
    // Rounded rectangle body, drawn clockwise from the top-left straight edge.
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(7f, 11f)
        lineTo(17f, 11f)
        curveTo(18.66f, 11f, 20f, 12.34f, 20f, 14f)
        lineTo(20f, 18f)
        curveTo(20f, 19.66f, 18.66f, 21f, 17f, 21f)
        lineTo(7f, 21f)
        curveTo(5.34f, 21f, 4f, 19.66f, 4f, 18f)
        lineTo(4f, 14f)
        curveTo(4f, 12.34f, 5.34f, 11f, 7f, 11f)
        close()
    }
    // Shackle. SVG closed: M8 11V8a4 4 0 0 1 8 0v3
    //          SVG open:   M8 11V8a4 4 0 0 1 7-2.6
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(8f, 11f)
        lineTo(8f, 8f)
        if (locked) {
            // Half circle radius 4 centred at (12, 8): (8,8) -> (12,4) -> (16,8)
            curveTo(8f, 5.79f, 9.79f, 4f, 12f, 4f)
            curveTo(14.21f, 4f, 16f, 5.79f, 16f, 8f)
            lineTo(16f, 11f)
        } else {
            // Open shackle: (8,8) -> (12,4) -> (15,5.4), stopping short so the
            // hook stands clear of the body's right edge.
            curveTo(8f, 5.79f, 9.79f, 4f, 12f, 4f)
            curveTo(13.19f, 4f, 14.26f, 4.52f, 15f, 5.35f)
        }
    }
}.build()
