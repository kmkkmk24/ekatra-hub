package com.kaushikkm.ekatrahub.ui.timeline

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.kaushikkm.ekatrahub.R
import com.kaushikkm.ekatrahub.data.AppCount
import com.kaushikkm.ekatrahub.data.NotificationEntity

/**
 * The unified timeline. Used in two places:
 *  - the app itself, and
 *  - the Peek panel over other apps ([inOverlay] = true). Pop-up dialogs can't open from an
 *    overlay window, so "clear history" is only offered inside the app.
 *
 * Layout, like the BB10 Hub: a vertical rail of app icons on the left (tap one to filter),
 * the timeline on the right.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    vm: TimelineViewModel = viewModel(),
    inOverlay: Boolean = false,
    onClose: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    banner: (@Composable () -> Unit)? = null,
    onLogoClick: (() -> Unit)? = null,
) {
    val items = vm.items.collectAsLazyPagingItems()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val selectedApp by vm.selectedApp.collectAsStateWithLifecycle()

    // Search hides behind the 🔍 icon. Closing it always clears it, so a forgotten search
    // can never silently filter the timeline.
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(searchOpen) {
        if (searchOpen) {
            runCatching { searchFocus.requestFocus() }
        } else {
            searchText = ""
            vm.setQuery("")
        }
    }

    var showClearDialog by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    val openItem: (NotificationEntity) -> Unit = { item ->
        vm.open(item)
        onClose?.invoke() // in Peek: get out of the way of the app being opened
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = if (inOverlay) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            TopAppBar(
                title = { EkatraLogo(onClick = onLogoClick) },
                actions = {
                    IconButton(onClick = { searchOpen = !searchOpen }) {
                        Icon(
                            if (searchOpen) Icons.Filled.Clear else Icons.Filled.Search,
                            contentDescription = if (searchOpen) "Close search" else "Search",
                        )
                    }
                    if (!inOverlay) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Clear history")
                        }
                    }
                    if (onOpenSettings != null) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    }
                    if (onClose != null) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    }
                },
                windowInsets = if (inOverlay) WindowInsets(0) else TopAppBarDefaults.windowInsets,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            AnimatedVisibility(visible = searchOpen) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = {
                        searchText = it
                        vm.setQuery(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        .focusRequester(searchFocus),
                    placeholder = { Text("Search notifications") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = CircleShape,
                )
            }

            banner?.invoke()

            Row(Modifier.fillMaxSize()) {
                if (apps.isNotEmpty()) {
                    AppRail(apps = apps, selected = selectedApp, onSelect = vm::selectApp)
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }

                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                val isEmpty = items.itemCount == 0 && items.loadState.refresh is LoadState.NotLoading
                if (isEmpty) {
                    EmptyState(
                        query = searchText.trim(),
                        appName = apps.firstOrNull { it.packageName == selectedApp }?.appLabel,
                        onClearSearch = { searchOpen = false },
                    )
                } else {
                    LazyColumn(
                        // weight(1f): the list takes the space left above the swipe bar.
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 8.dp),
                    ) {
                        items(
                            count = items.itemCount,
                            // Stable keys + content type let Compose reuse rows instead of rebuilding them.
                            key = items.itemKey { it.id },
                            contentType = items.itemContentType { "notification" },
                        ) { index ->
                            val item = items[index] ?: return@items
                            NotificationRow(
                                item = item,
                                onOpen = { openItem(item) },
                                onToggleRead = { vm.toggleRead(item) },
                                onRemove = { vm.remove(item) },
                                canReply = { vm.canReply(item) },
                                onReply = { text -> vm.reply(item, text) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    // Swipe bar at the bottom, within thumb reach. Acts on what's shown:
                    // the selected app, or all notifications.
                    val selected = apps.firstOrNull { it.packageName == selectedApp }
                    BulkSwipeBar(
                        target = selected?.appLabel ?: "notifications",
                        onMarkAllRead = { vm.markAllRead(selectedApp) },
                        onRemoveAll = { vm.removeAll(selectedApp) },
                    )
                }
                }
            }
        }
    }

    if (showClearDialog) {
        val appName = apps.firstOrNull { it.packageName == selectedApp }?.appLabel
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(if (appName != null) "Clear $appName history?" else "Clear all history?") },
            text = {
                Text(
                    "This removes the saved notifications from Ekatra only. " +
                        "Your messages in the apps themselves are not touched.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.clear()
                    showClearDialog = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            },
        )
    }
}

/** The small Ekatra mark used instead of a text title. In Peek, tapping it opens the app. */
@Composable
private fun EkatraLogo(onClick: (() -> Unit)?) {
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0xFF2B1B3D))
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Open Ekatra", onClick = onClick) else Modifier),
    ) {
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "Ekatra",
            modifier = Modifier
                .fillMaxSize()
                .scale(1.5f),
        )
    }
}

/** BB10-style vertical rail: "All" on top, then one icon per app with its unread count. */
@Composable
private fun AppRail(apps: List<AppCount>, selected: String?, onSelect: (String?) -> Unit) {
    val totalUnread = remember(apps) { apps.sumOf { it.unread } }
    LazyColumn(
        modifier = Modifier
            .width(68.dp)
            .fillMaxHeight(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "all") {
            RailItem(label = "All apps", unread = totalUnread, selected = selected == null, onClick = { onSelect(null) }) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        items(apps, key = { it.packageName }) { app ->
            RailItem(
                label = app.appLabel,
                unread = app.unread,
                selected = selected == app.packageName,
                onClick = { onSelect(app.packageName) },
            ) {
                AppIcon(app.packageName, app.appLabel, size = 36.dp)
            }
        }
    }
}

/**
 * The swipe bar at the top of the notification list. It acts on what's showing:
 * the selected app, or all notifications when "All" is selected.
 *  - drag the › right past a third: mark them all read
 *  - drag it almost all the way:     remove them all
 * The bar fills as you drag and says what will happen when you let go.
 */
@Composable
private fun BulkSwipeBar(target: String, onMarkAllRead: () -> Unit, onRemoveAll: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val thumbPx = with(density) { 36.dp.toPx() }
    var width by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableFloatStateOf(0f) }
    var stage by remember { mutableStateOf(SwipeStage.None) }

    val colors = MaterialTheme.colorScheme
    val (fill, accent, onAccent) = when (stage) {
        SwipeStage.Remove -> Triple(colors.error.copy(alpha = 0.25f), colors.error, colors.onError)
        SwipeStage.Read -> Triple(colors.primary.copy(alpha = 0.22f), colors.primary, colors.onPrimary)
        SwipeStage.None -> Triple(colors.primary.copy(alpha = 0.10f), colors.secondaryContainer, colors.onSecondaryContainer)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainerHigh)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .draggable(
                state = rememberDraggableState { delta ->
                    offset = (offset + delta).coerceIn(0f, width)
                    val newStage = SwipeStage.of(offset / width)
                    if (newStage != stage) {
                        stage = newStage
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                },
                orientation = Orientation.Horizontal,
                onDragStopped = {
                    when (stage) {
                        SwipeStage.Remove -> onRemoveAll()
                        SwipeStage.Read -> onMarkAllRead()
                        SwipeStage.None -> Unit
                    }
                    stage = SwipeStage.None
                    animate(offset, 0f, animationSpec = tween(180)) { value, _ -> offset = value }
                },
            )
            .semantics { contentDescription = "Swipe right to mark all $target read, all the way to remove all" },
    ) {
        // Fill that grows from the left as you drag (GPU-only, nothing re-laid-out per frame).
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = (offset / width).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .background(fill),
        )
        Text(
            when (stage) {
                SwipeStage.None -> "Swipe ›  mark all read · all the way  remove"
                SwipeStage.Read -> "Release: mark all $target read"
                SwipeStage.Remove -> "Release: remove all $target"
            },
            modifier = Modifier
                .align(Alignment.Center)
                .padding(start = 40.dp, end = 12.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (stage == SwipeStage.None) colors.onSurfaceVariant else accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // The thumb you drag.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer { translationX = offset.coerceAtMost(width - thumbPx).coerceAtLeast(0f) }
                .padding(2.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = onAccent,
            )
        }
    }
}

@Composable
private fun RailItem(
    label: String,
    unread: Int,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$label, $unread unread" },
        contentAlignment = Alignment.Center,
    ) {
        icon()
        if (unread > 0) {
            Badge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text(if (unread > 99) "99+" else unread.toString())
            }
        }
    }
}

private enum class SwipeMode { None, Read, Panel }

/** How far a right-swipe has gone, as a fraction of the width. Shared by rows and the rail bar. */
private enum class SwipeStage {
    None, Read, Remove;

    companion object {
        const val READ_AT = 0.35f
        const val REMOVE_AT = 0.8f
        fun of(fraction: Float) = when {
            fraction >= REMOVE_AT -> Remove
            fraction >= READ_AT -> Read
            else -> None
        }
    }
}

/**
 * One notification.
 *  - Tap: open it in its app.
 *  - Tap ⌄ (or long-press): expand - full message and a reply box.
 *  - Swipe right: mark read / unread.
 *  - Swipe left (inside Peek): push Peek back to the edge.
 * (Directions are mirrored when the Peek handle is on the right edge.)
 *
 * Read notifications are greyed out: faded text and a black-and-white app icon.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationRow(
    item: NotificationEntity,
    onOpen: () -> Unit,
    onToggleRead: () -> Unit,
    onRemove: () -> Unit,
    canReply: () -> Boolean,
    onReply: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val panelDrag = LocalPanelDrag.current
    val readDirection = panelDrag?.let { -it.closeDirection } ?: 1f
    // Two stages, measured against the row's width:
    //   past READ_AT    -> "Mark read"  (blue)
    //   past REMOVE_AT  -> "Remove"     (red)  - swiping almost all the way
    var rowWidth by remember { mutableFloatStateOf(1f) }

    // How far the row has been swiped in the "read" direction. Read only in graphicsLayer, so
    // swiping just slides the already-drawn row - smooth even on long lists.
    var offset by remember { mutableFloatStateOf(0f) }
    var mode by remember { mutableStateOf(SwipeMode.None) }
    var stage by remember { mutableStateOf(SwipeStage.None) }

    val dragState = rememberDraggableState { delta ->
        val towardRead = delta * readDirection
        if (mode == SwipeMode.None) {
            mode = if (towardRead < 0 && panelDrag != null) SwipeMode.Panel else SwipeMode.Read
        }
        when (mode) {
            SwipeMode.Read -> {
                offset = (offset + towardRead).coerceIn(0f, rowWidth)
                val newStage = SwipeStage.of(offset / rowWidth)
                if (newStage != stage) {
                    stage = newStage
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
            SwipeMode.Panel -> panelDrag?.onDrag(-towardRead)
            SwipeMode.None -> Unit
        }
    }

    val time = remember(item.postedAt) {
        DateUtils.getRelativeTimeSpanString(
            item.postedAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        ).toString()
    }

    // Read = greyed out. Animated so you see it fade as you let go of the swipe.
    val contentAlpha by animateFloatAsState(if (item.isRead && !expanded) 0.5f else 1f, label = "readFade")

    // Set the instant a full swipe is released: the row collapses straight away instead of
    // waiting for the database delete and list refresh to catch up.
    var removed by remember { mutableStateOf(false) }

    Box(
        modifier
            .then(if (removed) Modifier.height(0.dp) else Modifier)
            .onSizeChanged { if (!removed) rowWidth = it.width.toFloat().coerceAtLeast(1f) }
            .draggable(
            state = dragState,
            orientation = Orientation.Horizontal,
            onDragStopped = { velocity ->
                when (mode) {
                    SwipeMode.Read -> {
                        when (stage) {
                            SwipeStage.Remove -> {
                                // Remove right away (in the background) while a quick 120 ms
                                // slide finishes the motion, then collapse the row instantly.
                                onRemove()
                                animate(offset, rowWidth, animationSpec = tween(120)) { value, _ -> offset = value }
                                removed = true
                            }
                            SwipeStage.Read -> {
                                onToggleRead()
                                animate(offset, 0f, animationSpec = tween(180)) { value, _ -> offset = value }
                            }
                            SwipeStage.None -> animate(offset, 0f, animationSpec = tween(180)) { value, _ -> offset = value }
                        }
                        stage = SwipeStage.None
                    }
                    SwipeMode.Panel -> panelDrag?.onDragEnd(-velocity * readDirection)
                    SwipeMode.None -> Unit
                }
                mode = SwipeMode.None
            },
        ),
    ) {
        // Revealed behind the row while swiping towards "read".
        if (offset > 0f) {
            val colors = MaterialTheme.colorScheme
            val (bg, tint) = when (stage) {
                SwipeStage.Remove -> colors.error to colors.onError
                SwipeStage.Read -> colors.primary to colors.onPrimary
                SwipeStage.None -> colors.primaryContainer to colors.onPrimaryContainer
            }
            Row(
                Modifier
                    .matchParentSize()
                    .background(bg)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (readDirection > 0) Arrangement.Start else Arrangement.End,
            ) {
                if (stage == SwipeStage.Remove) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = tint)
                    Spacer(Modifier.width(8.dp))
                    Text("Remove", color = tint, style = MaterialTheme.typography.labelLarge)
                } else {
                    Icon(Icons.Filled.Done, contentDescription = null, tint = tint)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (item.isRead) "Mark unread" else "Mark read",
                        color = tint,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        Column(
            Modifier
                .graphicsLayer { translationX = offset * readDirection }
                .background(if (expanded) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.background),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { if (expanded) expanded = false else onOpen() },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            expanded = !expanded
                        },
                        onClickLabel = "Open in ${item.appLabel}",
                        onLongClickLabel = "Show full message",
                    )
                    .graphicsLayer { alpha = contentAlpha }
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
            ) {
                AppIcon(item.packageName, item.appLabel, size = 36.dp, grayscale = item.isRead)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.title.ifBlank { item.appLabel },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (item.isRead) FontWeight.Normal else FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        if (!item.isRead) {
                            Box(
                                Modifier
                                    .padding(end = 6.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                        Text(
                            time,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Expanded shows everything the notification carried (whole chat, all lines).
                    val body = if (expanded) item.fullText ?: item.text else item.text
                    if (body.isNotBlank()) {
                        Text(
                            body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Show full message and reply",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Expanded = the full message (above) plus a reply box when the app supports it,
            // or a short note explaining what else is (or isn't) available.
            val replyable = remember(expanded, item.sbnKey) { expanded && canReply() }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(start = 60.dp, end = 12.dp, bottom = 12.dp)) {
                    when {
                        replyable -> ReplyBox(onSend = onReply)
                        item.hadReply -> ExpandedNote(
                            "Reply isn't available for this one any more (Ekatra restarted since it arrived). " +
                                "Tap it to reply in ${item.appLabel}.",
                        )
                        item.fullText == null -> ExpandedNote(
                            "That's everything ${item.appLabel} put in this notification. Tap it to open the full story in the app.",
                        )
                    }
                }
            }
            HorizontalDivider(
                Modifier.padding(start = 60.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun ExpandedNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
    )
}

/** Inline reply box. The message is sent by the app itself (WhatsApp, Telegram, SMS...). */
@Composable
private fun ReplyBox(onSend: (String) -> Boolean) {
    var text by rememberSaveable { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            status = null
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus),
        placeholder = { Text("Reply") },
        shape = RoundedCornerShape(20.dp),
        maxLines = 4,
        trailingIcon = {
            IconButton(
                enabled = text.isNotBlank(),
                onClick = {
                    if (onSend(text.trim())) {
                        text = ""
                        status = "Sent ✓"
                    } else {
                        status = "Can't reply to this one any more - open it in the app instead."
                    }
                },
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send reply")
            }
        },
        supportingText = status?.let { message -> { Text(message) } },
    )
}

@Composable
private fun EmptyState(query: String, appName: String?, onClearSearch: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val (title, body) = when {
                query.isNotEmpty() && appName != null ->
                    "No results for “$query”" to "Nothing in $appName matches your search."
                query.isNotEmpty() ->
                    "No results for “$query”" to "Try a different word."
                appName != null ->
                    "Nothing from $appName" to "Its notifications will appear here."
                else ->
                    "All quiet for now" to "New notifications will appear here as they arrive. Ask a friend to send you a message to test it."
            }
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.size(4.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (query.isNotEmpty()) {
                Spacer(Modifier.size(12.dp))
                OutlinedButton(onClick = onClearSearch) { Text("Clear search") }
            }
        }
    }
}
