/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.ui.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurora.extensions.requiresObbDir
import com.aurora.store.MainViewModel
import com.aurora.store.R
import com.aurora.store.compose.composable.TopAppBar
import com.aurora.store.compose.composition.LocalNetworkStatus
import com.aurora.store.compose.navigation.Destination
import com.aurora.store.compose.ui.apps.AppsGamesScreen
import com.aurora.store.compose.ui.commons.MoreSheet
import com.aurora.store.compose.ui.commons.NetworkScreen
import com.aurora.store.compose.ui.sheets.AppUpdateSheet
import com.aurora.store.compose.ui.updates.UpdatesScreen
import com.aurora.store.data.model.NetworkStatus
import com.aurora.store.data.model.PermissionType
import com.aurora.store.data.providers.PermissionProvider.Companion.isGranted
import com.aurora.store.data.room.update.Update
import com.aurora.store.viewmodel.all.UpdatesViewModel
import kotlinx.coroutines.launch

private enum class MainTab(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int
) {
    APPS(R.string.title_apps, R.drawable.ic_apps),
    GAMES(R.string.title_games, R.drawable.ic_games),
    UPDATES(R.string.title_updates, R.drawable.ic_updates)
}

@Composable
fun MainScreen(
    initialTab: Int = 0,
    mainViewModel: MainViewModel = hiltViewModel(),
    updatesViewModel: UpdatesViewModel = hiltViewModel(),
    onNavigateTo: (Destination) -> Unit = {},
    /**
     * When `true`, the TopAppBar / NavigationBar are replaced with custom compact `Row`s whose
     * heights are small enough that the top bar's bottom edge sits higher ("moves up") and the
     * bottom bar's top edge sits lower ("moves down"), freeing vertical space for the pager
     * content in the middle of a round watch.
     *
     * We do NOT shrink the Material3 `TopAppBar` / `NavigationBar` with `Modifier.height()` —
     * those components enforce their own internal heights (64dp / 80dp) and window insets, so
     * forcing a smaller height clips the title text and icons instead of re-laying them out.
     * The custom `Row`s below use appropriately sized icons (18dp / 20dp) and touch targets
     * (32dp / 36dp) so nothing is clipped — the bars are genuinely shorter, not cut off.
     *
     * Phones/tablets pass `false` (default) and get the standard Material3 components.
     */
    wearCompact: Boolean = false
) {
    val context = LocalContext.current
    val networkStatus = LocalNetworkStatus.current
    val updates by mainViewModel.updateHelper.updates.collectAsStateWithLifecycle(
        initialValue = null
    )
    val updateCount = updates?.size ?: 0

    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = initialTab.coerceIn(
            0,
            MainTab.entries.size - 1
        )
    ) {
        MainTab.entries.size
    }

    var showMoreSheet by remember { mutableStateOf(false) }
    var appUpdateTarget by remember { mutableStateOf<Update?>(null) }

    fun handleNavigation(destination: Destination) {
        when (destination) {
            is Destination.AppUpdate -> appUpdateTarget = destination.update
            else -> onNavigateTo(destination)
        }
    }

    if (networkStatus == NetworkStatus.UNAVAILABLE) {
        NetworkScreen()
        return
    }

    if (showMoreSheet) {
        MoreSheet(
            onDismiss = { showMoreSheet = false },
            onNavigateTo = { destination ->
                showMoreSheet = false
                onNavigateTo(destination)
            }
        )
    }

    appUpdateTarget?.let { app ->
        AppUpdateSheet(
            update = app,
            onDismiss = { appUpdateTarget = null },
            onNavigateTo = { destination ->
                appUpdateTarget = null
                onNavigateTo(destination)
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (wearCompact) {
                WearCompactTopBar(
                    title = stringResource(MainTab.entries[pagerState.currentPage].labelRes),
                    onDownloads = { onNavigateTo(Destination.Downloads) },
                    onMore = { showMoreSheet = true }
                )
            } else {
                TopAppBar(
                    title = stringResource(MainTab.entries[pagerState.currentPage].labelRes),
                    actions = {
                        IconButton(onClick = { onNavigateTo(Destination.Downloads) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_download_manager),
                                contentDescription = stringResource(R.string.title_download_manager)
                            )
                        }
                        IconButton(onClick = { showMoreSheet = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_settings_account),
                                contentDescription = stringResource(R.string.title_more)
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateTo(Destination.Search) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_round_search),
                    contentDescription = stringResource(R.string.action_search)
                )
            }
        },
        bottomBar = {
            if (wearCompact) {
                WearCompactBottomBar(
                    currentPage = pagerState.currentPage,
                    updateCount = updateCount,
                    onTabSelected = { index ->
                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                    }
                )
            } else {
                NavigationBar {
                    MainTab.entries.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch { pagerState.animateScrollToPage(index) }
                            },
                            icon = {
                                if (tab == MainTab.UPDATES && updateCount > 0) {
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
                            },
                            label = { Text(stringResource(tab.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .fillMaxSize()
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                beyondViewportPageCount = MainTab.entries.size - 1,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (MainTab.entries[page]) {
                    MainTab.APPS -> AppsGamesScreen(
                        pageType = 0,
                        onNavigateTo = onNavigateTo
                    )
                    MainTab.GAMES -> AppsGamesScreen(
                        pageType = 1,
                        onNavigateTo = ::handleNavigation
                    )
                    MainTab.UPDATES -> UpdatesScreen(
                        viewModel = updatesViewModel,
                        onNavigateTo = ::handleNavigation,
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
                            if (needsObb && !isGranted(context, PermissionType.STORAGE_MANAGER)) {
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
}

/**
 * Wear OS compact top bar — a plain [Row] with a fixed 36dp height (vs the Material3
 * [TopAppBar]'s 64dp + window insets). Uses 18dp icons inside 32dp circular touch targets so
 * nothing is clipped: the bar is genuinely shorter, its bottom edge sits higher ("moves up"),
 * and the freed space goes to the pager content below.
 */
@Composable
private fun WearCompactTopBar(
    title: String,
    onDownloads: () -> Unit,
    onMore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onDownloads),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_download_manager),
                contentDescription = stringResource(R.string.title_download_manager),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onMore),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_settings_account),
                contentDescription = stringResource(R.string.title_more),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Wear OS compact bottom bar — a plain [Row] with a fixed 44dp height (vs the Material3
 * [NavigationBar]'s 80dp). Uses 20dp icons inside 36dp circular touch targets so nothing is
 * clipped: the bar is genuinely shorter, its top edge sits lower ("moves down"), and the freed
 * space goes to the pager content above.
 */
@Composable
private fun WearCompactBottomBar(
    currentPage: Int,
    updateCount: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        MainTab.entries.forEachIndexed { index, tab ->
            val selected = currentPage == index
            val tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onTabSelected(index) },
                contentAlignment = Alignment.Center
            ) {
                if (tab == MainTab.UPDATES && updateCount > 0) {
                    BadgedBox(badge = { Badge { Text("$updateCount") } }) {
                        Icon(
                            painter = painterResource(tab.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = tint
                        )
                    }
                } else {
                    Icon(
                        painter = painterResource(tab.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = tint
                    )
                }
            }
        }
    }
}
