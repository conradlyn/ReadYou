package me.ash.reader.ui.component

import android.os.Build
import android.view.SoundEffectConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.ash.reader.domain.model.general.Filter
import me.ash.reader.infrastructure.preference.FlowFilterBarStylePreference
import me.ash.reader.infrastructure.preference.LocalThemeIndex
import me.ash.reader.ui.adaptive.adaptiveSize
import me.ash.reader.ui.ext.surfaceColorAtElevation
import me.ash.reader.ui.theme.palette.onDark

/**
 * @param contentPadding horizontal inset applied to the items while the surface behind them stays
 *   full-bleed. Callers on a wide window pass the adaptive gutter so the items line up with the
 *   centred content column instead of being pinned to the left edge. Deliberately opt-in and
 *   defaulted to zero: this composable is also used inside a pane (FlowPage) and inside the style
 *   previews, where a window-width gutter would over-inset.
 * @param leading optional extra action pinned to the left of the filter items, inside the same
 *   padding as they are. Defaulted to null so every existing call site keeps the upstream layout,
 *   and so a merge from upstream sees at most a two-line conflict here.
 * @param labelStyle style for the filter labels. Defaulted to null, which leaves the inherited
 *   `LocalTextStyle` in place - byte-identical to upstream, where the `style` argument is not passed
 *   at all. Callers that want the bar to follow their page's list font pass the same style the page
 *   gives its list rows, so the bar stops being the one surface that ignores the font setting.
 */
@Composable
fun FilterBar(
    modifier: Modifier = Modifier,
    filter: Filter,
    filterBarStyle: Int,
    filterBarFilled: Boolean,
    filterBarPadding: Dp,
    filterBarTonalElevation: Dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    leading: (@Composable () -> Unit)? = null,
    labelStyle: TextStyle? = null,
    filterOnClick: (Filter) -> Unit = {},
) {
    val view = LocalView.current
    val themeIndex = LocalThemeIndex.current
    val indicatorColor = if (themeIndex == 5 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    } onDark MaterialTheme.colorScheme.secondaryContainer

    // Scaled with the icons it holds: the bar exists to give them room, so growing one without the
    // other would just add padding.
    val containerHeight = adaptiveSize(
        when (filterBarStyle) {
            FlowFilterBarStylePreference.Icon.value -> 64.dp
            else -> 80.dp
        }
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(filterBarTonalElevation),
        modifier = modifier
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                .padding(contentPadding)
                .defaultMinSize(minHeight = containerHeight)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            Spacer(modifier = Modifier.width(filterBarPadding))
            leading?.let { item ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    item()
                }
            }
            Filter.values.forEach { item ->
                NavigationBarItem(
                    // The explicit weight is what makes the leading slot and the filter items divide
                    // the row the same way. A NavigationBarItem only lays out as a peer of its
                    // siblings inside a NavigationBar, which is a Row that hands it a weight; in this
                    // plain Row it keeps its own content width. Without the weight the filters would
                    // stretch over everything but the leading action's 40dp, which is exactly the
                    // lopsided bar this parameter has to avoid. Applying it here keeps the split
                    // independent of what the item does internally.
                    modifier = Modifier.weight(1f).height(containerHeight),
                    alwaysShowLabel = when (filterBarStyle) {
                        FlowFilterBarStylePreference.Icon.value -> false
                        FlowFilterBarStylePreference.IconLabel.value -> true
                        FlowFilterBarStylePreference.IconLabelOnlySelected.value -> false
                        else -> false
                    },
                    icon = {
                        Icon(
                            modifier = Modifier.size(adaptiveSize(24.dp)),
                            imageVector = if (filter == item && filterBarFilled) {
                                item.iconFilled
                            } else {
                                item.iconOutline
                            },
                            contentDescription = item.toName()
                        )
                    },
                    label = if (filterBarStyle == FlowFilterBarStylePreference.Icon.value) {
                        null
                    } else {
                        {
                            Text(
                                text = item.toName(),
                                // Null leaves `Text`'s own default in place, which is the inherited
                                // `LocalTextStyle` that `NavigationBarItem` provides - i.e. exactly
                                // what upstream gets by not passing `style` at all.
                                style = labelStyle ?: LocalTextStyle.current,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    selected = filter == item,
                    onClick = {
//                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        view.playSoundEffect(SoundEffectConstants.CLICK)
                        filterOnClick(item)
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = indicatorColor,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedIconColor = MaterialTheme.colorScheme.contentColorFor(indicatorColor),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
            Spacer(modifier = Modifier.width(filterBarPadding))
        }
    }
}
