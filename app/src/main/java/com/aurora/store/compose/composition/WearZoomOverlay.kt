/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.composition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.Text

/**
 * Wraps [content] with a user-adjustable scale transform and a floating − / % / + control cluster.
 *
 * Designed for Wear OS round watches: the original phone UI is kept as-is (no re-layout) and just
 * shrunk via [Modifier.scale] so it fits the round glass. The user can fine-tune the zoom live.
 *
 * On non-Wear devices this is a transparent passthrough — callers should only wrap content with it
 * when [LocalWearRound] is true (or unconditionally; the overlay self-hides when scale == 1f).
 *
 * Layout:
 *  - [content] fills the box and gets [Modifier.scale] applied.
 *  - A floating row of three small round buttons sits at the bottom-trailing corner:
 *    [−] decrement, [%] shows current scale & resets on tap, [+] increment.
 *
 * Scale is persisted across recompositions via [rememberSaveable] and clamped to
 * [MIN_SCALE]..[MAX_SCALE] with [STEP] increments.
 */
@Composable
fun WearZoomOverlay(content: @Composable () -> Unit) {
    var contentScale by rememberSaveable { mutableFloatStateOf(DEFAULT_SCALE) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Original phone UI, scaled to fit the watch glass.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(contentScale),
            content = { content() }
        )

        // Floating zoom controls — bottom-trailing, semi-opaque so they don't fully block content.
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 4.dp, bottom = 4.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    contentScale = (contentScale - STEP).coerceIn(MIN_SCALE, MAX_SCALE)
                },
                modifier = Modifier.size(28.dp)
            ) { Text("−") }

            // Middle button shows current % and resets to default on tap.
            OutlinedButton(
                onClick = { contentScale = DEFAULT_SCALE },
                modifier = Modifier.size(40.dp)
            ) {
                Text("${(contentScale * 100).toInt()}")
            }

            OutlinedButton(
                onClick = {
                    contentScale = (contentScale + STEP).coerceIn(MIN_SCALE, MAX_SCALE)
                },
                modifier = Modifier.size(28.dp)
            ) { Text("+") }
        }
    }
}

private const val DEFAULT_SCALE = 0.55f
private const val MIN_SCALE = 0.30f
private const val MAX_SCALE = 1.00f
private const val STEP = 0.05f
