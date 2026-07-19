package com.liquidglass.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Device tilt, each axis in -1..1. Fed by the accelerometer in MainActivity
 * and used to slide highlights across glass surfaces so they feel physical.
 */
data class Tilt(val x: Float, val y: Float)

/**
 * The Liquid Glass surface treatment:
 *  - translucent frosted fill (a soft white gradient over whatever is behind)
 *  - a 1dp rim whose brightness shifts with device tilt — the "light catching
 *    the edge" effect
 *  - a specular sheen (soft radial light spot) that slides with tilt
 */
fun Modifier.glass(shape: Shape, tilt: Tilt, tintAlpha: Float = 0.10f): Modifier =
    this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = tintAlpha + 0.08f),
                    Color.White.copy(alpha = tintAlpha),
                    Color.White.copy(alpha = tintAlpha + 0.03f),
                ),
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.60f),
                    Color.White.copy(alpha = 0.08f),
                    Color.White.copy(alpha = 0.35f),
                ),
                start = Offset(600f * (0.5f + tilt.x * 0.5f), 0f),
                end = Offset(600f * (0.5f - tilt.x * 0.5f), 1200f),
            ),
            shape = shape,
        )
        .drawWithContent {
            drawContent()
            if (size.minDimension > 0f) {
                val cx = size.width * (0.35f + 0.35f * tilt.x)
                val cy = size.height * (0.15f + 0.25f * tilt.y)
                val r = size.maxDimension * 0.65f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = r,
                    ),
                    radius = r,
                    center = Offset(cx, cy),
                )
            }
        }
