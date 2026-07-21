/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.ui.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ScreenScaffold
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
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int
) {
    APPS(R.string.title_apps, R.drawable.ic_apps),
    GAMES(R.string.title_games, R.drawable.ic_games),
    UPDATES(R.string.title_updates, R.drawable.ic_updates)
}

/**
 * Wear OS (round watch) variant of [MainScreen].
 *
 * The phone-oriented [MainScreen] stacks a `TopAppBar` + `NavigationBar` + `FloatingActionButton`
 * onto a `HorizontalPager` — on a round watch those three chrome bars eat almost the whole
 * vertical space and the pager content is reduced to a thin sliver.
 *
 * This variant drops all three bars and rebuilds the screen around Wear Material3 primitives:
 *  - [AppScaffold] / [ScreenScaffold] apply the circular inset + chin automatically.
 *  - The tab switcher becomes a `ScalingLazyColumn` whose first item is a compact chip row
 *    (Apps / Games / Updates + Search / Downloads / More). The selected tab uses a secondary
 *    chip color so the user can see which page they're on at a glance.
 *  - Below the chips, a `HorizontalPager` shows the corresponding page content
 *    (Apps / Games / Updates). Swiping the pager is disabled — the chips drive navigation,
 *    which is more reliable on a small round screen where stray swipes are common.
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

    val lazyListState = rememberScalingLazyListState()

    if (networkStatus == NetworkStatus.UNAVAILABLE) {
        NetworkScreen()
        return
    }

    AppScaffold {
        ScreenScaffold(scrollState = lazyListState) {
            ScalingLazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Tab buttons — Apps / Games / Updates. The selected tab uses a filled Button
                // (high-contrast) so the user sees which page they're on; unselected tabs use
                // OutlinedButton (low-contrast). Tapping a button switches the pager page.
                WearTab.entries.forEachIndexed { index, tab ->
                    item(key = "tab-$index") {
                        val selected = pagerState.currentPage == index
                        val tabIcon: @Composable () -> Unit = {
                            if (tab == WearTab.UPDATES && updateCount > 0) {
                                BadgedBox(badge = { Badge { Text("$updateCount") } }) {
                                    Icon(
                                        painter = painterResource(tab.iconRes),
                                        contentDescription = null
                                    )
                                }
                            } else {
                                Icon(
                                    painter = painterResource(tab.iconRes),
                                    contentDescription = null
                                )
                            }
                        }
                        if (selected) {
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    tabIcon()
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(tab.labelRes))
                                }
                            }
                        } else {
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    tabIcon()
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(tab.labelRes))
                                }
                            }
                        }
                    }
                }

                // Page content — embedded in the same scrollable column so it benefits from
                // ScalingLazyColumn's auto-scaling near the round edges. Each page fills the
                // available width.
                item(key = "page-content") {
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = false,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        Box(modifier = Modifier.fillMaxSize()) {
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

                // Quick actions — search / downloads / more. These used to be TopAppBar actions
                // + FAB on phones; on a watch they fit better as outlined buttons below the
                // content (low-contrast so they don't compete with the active tab).
                item(key = "action-search") {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onNavigateTo(Destination.Search) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_round_search),
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_search))
                        }
                    }
                }
                item(key = "action-downloads") {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onNavigateTo(Destination.Downloads) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_download_manager),
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.title_download_manager))
                        }
                    }
                }
                item(key = "action-more") {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onNavigateTo(Destination.Settings) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_settings_account),
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.title_more))
                        }
                    }
                }
            }
        }
    }
}
