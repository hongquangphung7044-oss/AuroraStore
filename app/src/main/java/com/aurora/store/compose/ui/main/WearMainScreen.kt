/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.Text
import com.aurora.extensions.requiresObbDir
import com.aurora.store.MainViewModel
import com.aurora.store.R
import com.aurora.store.compose.composition.LocalNetworkStatus
import com.aurora.store.compose.navigation.Destination
import com.aurora.store.compose.ui.apps.AppsGamesScreen
import com.aurora.store.compose.ui.commons.NetworkScreen
import com.aurora.store.compose.ui.updates.UpdatesScreen
import com.aurora.store.data.model.NetworkStatus
import com.aurora.store.data.model.PermissionType
import com.aurora.store.data.providers.PermissionProvider.Companion.isGranted
import com.aurora.store.viewmodel.all.UpdatesViewModel
import kotlinx.coroutines.launch

private enum class WearTab(
    val labelRes: Int,
    val iconRes: Int
) {
    APPS(R.string.title_apps, R.drawable.ic_apps),
    GAMES(R.string.title_games, R.drawable.ic_games),
    UPDATES(R.string.title_updates, R.drawable.ic_updates)
}

/**
 * Wear OS (round watch) variant of [MainScreen].
 *
 * Strategy: instead of trying to re-layout the phone-oriented [AppsGamesScreen] / [UpdatesScreen]
 * for a 1.4" round display (which produced an unreadable mess), we keep the original phone layout
 * *as-is* and apply a [Modifier.scale] transform to shrink the whole content to fit the watch
 * glass. The scale level is user-adjustable via a floating − / + button pair, so the user can dial
 * in the right balance of "everything visible" vs "text readable" for their specific watch.
 *
 * Layout (top to bottom, inside Wear Material3 [AppScaffold]):
 *  1. A compact row of tab buttons (Apps / Games / Updates) — selected tab uses a filled
 *     [Button], others use [OutlinedButton]. Tapping switches the pager page.
 *  2. The [HorizontalPager] content area, wrapped in [scaledContent] so the phone UI is shrunk
 *     to fit the remaining vertical space.
 *  3. A floating control cluster (bottom-trailing) with − / reset / + buttons for live zoom.
 *
 * The zoom level persists across recompositions via [rememberSaveable] and is clamped to a safe
 * range ([MIN_SCALE] .. [MAX_SCALE]).
 *
 * Phone/tablet/TV keep using the original [MainScreen]; this is only routed to from
 * [com.aurora.store.compose.navigation.NavDisplay] when `LocalUI == UI.WEAR`.
 */
@Composable
fun WearMainScreen(
    initialTab: Int = 0,
    mainViewModel: MainViewModel = hiltViewModel(),
    updatesViewModel: UpdatesViewModel = hiltViewModel(),
    onNavigateTo: (Destination) -> Unit = {}
) {
    val context = LocalContext.current
    val networkStatus = LocalNetworkStatus.current
    val updates by mainViewModel.updateHelper.updates.collectAsStateWithLifecycle(
        initialValue = null
    )
    val updateCount = updates?.size ?: 0

    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = initialTab.coerceIn(0, WearTab.entries.size - 1)
    ) {
        WearTab.entries.size
    }

    // User-adjustable zoom for the embedded phone UI. 0.55 is a good default for 1.4"–1.8"
    // watches: phone UI designed for ~360dp width fits comfortably inside ~200dp visible area.
    var contentScale by rememberSaveable { mutableFloatStateOf(DEFAULT_SCALE) }

    if (networkStatus == NetworkStatus.UNAVAILABLE) {
        NetworkScreen()
        return
    }

    AppScaffold {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Tab row — Apps / Games / Updates. Selected is filled, others outlined.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WearTab.entries.forEachIndexed { index, tab ->
                        val selected = pagerState.currentPage == index
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                coroutineScope.launch { pagerState.animateScrollToPage(index) }
                            }
                        ) {
                            if (tab == WearTab.UPDATES && updateCount > 0) {
                                BadgedBox(badge = { Badge { Text("$updateCount") } }) {
                                    Icon(
                                        painter = painterResource(tab.iconRes),
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                }
                            } else {
                                Icon(
                                    painter = painterResource(tab.iconRes),
                                    contentDescription = stringResource(tab.labelRes)
                                )
                            }
                        }
                    }
                }

                // Pager content — the phone UI, scaled to fit the watch. weight(1f) gives the
                // pager a finite height so AppsGamesScreen/UpdatesScreen measure correctly.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.TopCenter
                ) {
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = false,
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(contentScale)
                    ) { page ->
                        when (WearTab.entries[page]) {
                            WearTab.APPS -> AppsGamesScreen(
                                pageType = 0,
                                onNavigateTo = onNavigateTo
                            )
                            WearTab.GAMES -> AppsGamesScreen(
                                pageType = 1,
                                onNavigateTo = onNavigateTo
                            )
                            WearTab.UPDATES -> UpdatesScreen(
                                viewModel = updatesViewModel,
                                onNavigateTo = onNavigateTo,
                                onRequestUpdate = { update ->
                                    if (update.fileList.requiresObbDir() &&
                                        !isGranted(context, PermissionType.STORAGE_MANAGER)
                                    ) {
                                        onNavigateTo(
                                            Destination.PermissionRationale(
                                                setOf(PermissionType.STORAGE_MANAGER)
                                            )
                                        )
                                    } else {
                                        updatesViewModel.download(update)
                                    }
                                },
                                onRequestUpdateAll = { selectedUpdates ->
                                    val needsObb = selectedUpdates.any {
                                        it.fileList.requiresObbDir()
                                    }
                                    if (needsObb &&
                                        !isGranted(context, PermissionType.STORAGE_MANAGER)
                                    ) {
                                        onNavigateTo(
                                            Destination.PermissionRationale(
                                                setOf(PermissionType.STORAGE_MANAGER)
                                            )
                                        )
                                    } else {
                                        updatesViewModel.downloadAll(selectedUpdates)
                                    }
                                },
                                onCancelUpdate = { packageName ->
                                    updatesViewModel.cancelDownload(packageName)
                                },
                                onCancelAll = { updatesViewModel.cancelAll() }
                            )
                        }
                    }
                }
            }

            // Floating zoom controls — bottom-trailing, above the chin. − / + adjust the scale
            // by STEP; the middle button resets to DEFAULT_SCALE. Fades out below MIN_SCALE+ or
            // above MAX_SCALE- so the user sees they've hit the limit.
            ZoomControls(
                scale = contentScale,
                onDecrement = {
                    contentScale = (contentScale - STEP).coerceIn(MIN_SCALE, MAX_SCALE)
                },
                onReset = { contentScale = DEFAULT_SCALE },
                onIncrement = {
                    contentScale = (contentScale + STEP).coerceIn(MIN_SCALE, MAX_SCALE)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 4.dp)
            )
        }
    }
}

/**
 * Floating − / reset / + cluster for live zooming of the embedded phone UI.
 *
 * Drawn as three small round buttons in a row, semi-opaque background so they don't fully
 * obscure the underlying content.
 */
@Composable
private fun ZoomControls(
    scale: Float,
    onDecrement: () -> Unit,
    onReset: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onDecrement,
            modifier = Modifier.size(28.dp)
        ) { Text("−") }

        // Reset button — shows the current scale %, tapping resets to default.
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.size(40.dp)
        ) {
            Text("${(scale * 100).toInt()}")
        }

        OutlinedButton(
            onClick = onIncrement,
            modifier = Modifier.size(28.dp)
        ) { Text("+") }
    }
}

// Zoom constants — empirically tuned for 1.4"–1.8" Wear OS round watches.
private const val DEFAULT_SCALE = 0.55f
private const val MIN_SCALE = 0.30f
private const val MAX_SCALE = 1.00f
private const val STEP = 0.05f
