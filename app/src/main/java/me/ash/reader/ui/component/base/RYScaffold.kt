package me.ash.reader.ui.component.base

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.ash.reader.ui.adaptive.LocalAvailableWidthDp
import me.ash.reader.ui.adaptive.adaptiveSize
import me.ash.reader.ui.adaptive.rememberAdaptiveContentGutter
import me.ash.reader.ui.ext.surfaceColorAtElevation
import me.ash.reader.ui.theme.palette.onDark

@OptIn(ExperimentalMaterial3Api::class)
@Deprecated("Use m3 Scaffold instead")
@Composable
fun RYScaffold(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    topBarTonalElevation: Dp = 0.dp,
    containerTonalElevation: Dp = 0.dp,
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    floatingActionButton: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    // The width is measured here rather than read from the window so that it is the width this
    // page really gets. In the list pane of the list-detail layout that is the pane, which is
    // narrower than the window - the one case where the two disagree, and the reason this cannot
    // simply live next to the size class.
    BoxWithConstraints(modifier = modifier) {
        val gutter = rememberAdaptiveContentGutter(maxWidth)

        // Published so that the slots outside `content` - notably the filter bar in `bottomBar`,
        // which draws its own full-bleed surface - can line their items up with the centred
        // content column. See `rememberAdaptiveContentPadding`.
        CompositionLocalProvider(LocalAvailableWidthDp provides maxWidth) {
            Scaffold(
                modifier =
                    Modifier.background(
                        MaterialTheme.colorScheme.surfaceColorAtElevation(
                            topBarTonalElevation,
                            color = containerColor,
                        )
                    ),
                containerColor =
                    MaterialTheme.colorScheme.surfaceColorAtElevation(
                        containerTonalElevation,
                        color = containerColor,
                    ) onDark MaterialTheme.colorScheme.surface,
                topBar = {
                    if (topBar != null) topBar()
                    else if (navigationIcon != null || actions != null) {
                        TopAppBar(
                            title = {},
                            // Grows with the icons below it. The two have to move together: a 30dp
                            // glyph in a 64dp bar reads as cramped, and a 64dp bar with 30dp glyphs
                            // reads as a mistake. `expandedHeight` is the only way to change a
                            // Material 3 `TopAppBar` height - its internal Row hard-codes the
                            // token, so a `Modifier.height()` on the bar is ignored.
                            expandedHeight = adaptiveSize(TopAppBarDefaults.TopAppBarExpandedHeight),
                            navigationIcon = {
                                // Padding the icons rather than the bar: the bar keeps its own
                                // full-bleed surface, only the back arrow moves in, so it lines up
                                // with the content column instead of being pinned to the window
                                // edge. An explicit `topBar` is left alone on purpose - callers
                                // make the whole bar clickable (scroll to top), and shrinking it to
                                // the content column would take that hit target away.
                                Box(modifier = Modifier.padding(start = gutter)) {
                                    navigationIcon?.invoke()
                                }
                            },
                            actions = {
                                val scope = this
                                Box(modifier = Modifier.padding(end = gutter)) {
                                    actions?.invoke(scope)
                                }
                            },
                            colors =
                                TopAppBarDefaults.topAppBarColors(
                                    containerColor =
                                        MaterialTheme.colorScheme.surfaceColorAtElevation(
                                            topBarTonalElevation
                                        )
                                ),
                        )
                    }
                },
                content = {
                    val layoutDirection = LocalLayoutDirection.current
                    Column(
                        modifier =
                            Modifier.padding(
                                start = it.calculateStartPadding(layoutDirection) + gutter,
                                end = it.calculateEndPadding(layoutDirection) + gutter,
                            )
                    ) {
                        Spacer(modifier = Modifier.height(it.calculateTopPadding()))
                        content()
                    }
                },
                bottomBar = { bottomBar?.invoke() },
                floatingActionButton = { floatingActionButton?.invoke() },
                floatingActionButtonPosition = floatingActionButtonPosition,
            )
        }
    }
}
