/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.composition

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration

/**
 * `true` when the host window has a round viewport (e.g. most Wear OS watches).
 *
 * Composables that draw full-bleed backgrounds, edge-to-edge lists, or fixed corners should read
 * this to swap in circular-safe layouts (Wear Material3 `AppScaffold`, `ScalingLazyColumn`,
 * `Button`/`Chip`) so content is not clipped by the physical bezel.
 */
val LocalWearRound = staticCompositionLocalOf { false }

/**
 * Wraps [content] with Wear OS circular-display adaptation.
 *
 * On round watches this exposes [LocalWearRound] = `true` so descendants (e.g.
 * [WearSplashScreen]/[WearMainScreen]) can branch into Wear Material3 layouts. The actual
 * circular-clip + chin-safe insets are applied by Wear Material3's `AppScaffold`/`ScreenScaffold`
 * inside each screen — doing it here would double-clip.
 *
 * On rectangular screens it is a transparent passthrough, so phone/tablet/TV layouts are
 * completely unchanged.
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

    CompositionLocalProvider(LocalWearRound provides isRound) {
        Box(modifier = Modifier.fillMaxSize(), content = content)
    }
}
