/*
 * SPDX-FileCopyrightText: 2025 The Calyx Institute
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.composition

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Supported UI styles for different types of Android OS
 */
enum class UI {

    /**
     * Targets Phone, Foldable, Tablets, Desktop
     */
    DEFAULT,

    /**
     * Targets TV
     */
    TV,

    /**
     * Targets Wear OS (round watches). Composables can read [LocalWearRound] to know whether the
     * current watch has a circular display, and apply circular-safe insets/clip accordingly.
     */
    WEAR
}

/**
 * CompositionLocal to provide information on which UI style should be used
 */
val LocalUI = staticCompositionLocalOf { UI.DEFAULT }
