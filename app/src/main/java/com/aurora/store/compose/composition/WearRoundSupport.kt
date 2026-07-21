/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.composition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * `true` when the host window has a round viewport (e.g. most Wear OS watches).
 *
 * Composables that draw full-bleed backgrounds, edge-to-edge lists, or fixed corners should read
 * this to swap in [Modifier.roundScreenSafe] / circular clip so content is not clipped by the
 * physical bezel.
 */
val LocalWearRound = staticCompositionLocalOf { false }

/**
 * Minimum symmetric padding (in dp) applied to round screens so that content stays inside the
 * inscribed square of the circular viewport.
 *
 * 5% of a typical 1.84" Wear OS watch (~328px @ 320dpi ≈ 41mm) lands around 8dp, which matches
 * the chin inset Wear OS recommends for non-scrolling content.
 */
private val RoundScreenHorizontalPadding = 8.dp

/**
 * Applies round-screen-aware horizontal padding.
 *
 * On rectangular screens this is a no-op so phone/tablet/TV layouts are untouched.
 */
fun Modifier.roundScreenSafe(isRound: Boolean): Modifier = if (isRound) {
    this.padding(horizontal = RoundScreenHorizontalPadding)
} else {
    this
}

/**
 * Wraps [content] with circular-display adaptation when the current configuration reports a round
 * screen (i.e. Wear OS round watches).
 *
 * What it does on round watches:
 *  - Provides [LocalWearRound] = `true` so descendants can branch their own layout.
 *  - Clips the whole subtree to [CircleShape] so full-bleed backgrounds follow the physical
 *    glass instead of bleeding into the bezel.
 *  - Applies a symmetric horizontal inset via [roundScreenSafe].
 *
 * On rectangular screens it is a transparent passthrough.
 *
 * Note: this wraps the *content* of an activity, not the window itself — system bars are still
 * drawn by the platform; we only reshape the app surface the user actually sees.
 */
@Composable
fun WearRoundHost(content: @Composable BoxScope.() -> Unit) {
    val configuration = LocalConfiguration.current
    // Configuration#isScreenRound is the canonical Wear OS signal; it is hidden from the public
    // API but reachable through the runtime Configuration instance delivered to apps.
    val isRound = remember(configuration) {
        runCatching {
            val field = configuration.javaClass.getDeclaredField("isScreenRound")
            field.isAccessible = true
            field.getBoolean(configuration)
        }.getOrDefault(false)
    }

    if (isRound) {
        CompositionLocalProvider(LocalWearRound provides true) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color.Black)
                    .roundScreenSafe(isRound = true),
                content = content
            )
        }
    } else {
        CompositionLocalProvider(LocalWearRound provides false) {
            Box(
                modifier = Modifier.fillMaxSize(),
                content = content
            )
        }
    }
}
