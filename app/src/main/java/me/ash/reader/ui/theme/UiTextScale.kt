@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import me.ash.reader.infrastructure.preference.LocalUiTextScale

/**
 * The typography the app was given, before any UI scaling.
 *
 * Captured rather than re-read, which is what makes [ProvideUiTextScale] idempotent - see the note
 * on nesting below. `null` means "no scope has been entered yet", i.e. this is the outermost one.
 */
private val LocalUnscaledTypography = staticCompositionLocalOf<Typography?> { null }

/**
 * The factor [ProvideUiTextScale] actually applied, `1f` outside every scope.
 *
 * Separate from `LocalUiTextScale` (the raw preference) on purpose: a hardcoded `sp` has to grow
 * by the same amount the typography did, and only where the typography was grown. Reading the
 * preference directly would make `Banner` scale on a page that is not in scope.
 */
private val LocalAppliedUiTextScale = staticCompositionLocalOf { 1f }

/**
 * The app's own text size, applied to the settings pages and to every dialog.
 *
 * ## Why the typography is rebuilt rather than the density rescaled
 *
 * The obvious one-liner for "make all text bigger" is to override `LocalDensity` with a larger
 * `fontScale`, which scales every `sp` in the subtree in one go. It cannot be used here: a dialog
 * is composed into its own window, and `DialogLayout` is an `AbstractComposeView` whose
 * `ProvideAndroidCompositionLocals` **re-provides `LocalDensity`** (along with `LocalContext` and
 * `LocalConfiguration`) from that window. A `LocalDensity` set around a page therefore stops at
 * the dialog boundary, and half of this feature is the dialogs.
 *
 * ## Why this goes through `MaterialTheme` instead of `LocalTypography`
 *
 * Overriding `LocalTypography` directly is the natural thing to reach for and is **not possible**:
 * in material3 1.4.0 (the version this fork builds against - `compose-bom-alpha` 2025.10.01) the
 * accessor is `internal`, so it fails with
 * `Cannot access 'val LocalTypography': it is internal in file`.
 *
 * `MaterialTheme`'s `typography` parameter is the public door to the same value, and material3's
 * own documentation recommends this shape - "separate `MaterialTheme`(s) for different screens /
 * parts of your UI, overriding only the parts of the theme definition that need to change". See
 * [ProvideTypography] for why every other parameter is passed explicitly.
 *
 * ## Idempotent by construction, not by a guard
 *
 * A dialog opened from a settings page is inside *two* scopes. Rather than tracking "have I
 * applied this already" with a marker, [ProvideUiTextScale] always rebuilds from the captured
 * unscaled typography ([LocalUnscaledTypography]), never from the current `MaterialTheme.typography`
 * - so nesting it recomputes the same value instead of squaring the scale. The marker approach
 * would also be wrong here, because a marker set in the page would survive into the dialog window
 * while the thing it was guarding would not.
 *
 * ## What is deliberately left alone
 *
 * The list and reading pages. Their text styles are built as
 * `MaterialTheme.typography.<slot>.withFlowListStyle()`, i.e. the base size is read *from the
 * current typography* and then scaled by the user's per-page font preference. Scaling the
 * typography under them would size that text twice, so the scope is the settings routes
 * (`settingsNavEntry`) and the dialog wrapper (`RYDialog`) - not the app root.
 */
@Composable
fun ProvideUiTextScale(content: @Composable () -> Unit) {
    val scale = uiTextScaleFactor(LocalUiTextScale.current)
    val unscaled = LocalUnscaledTypography.current ?: MaterialTheme.typography

    // 100% has to leave the subtree *untouched*, not merely arithmetically unchanged. Re-entering
    // `MaterialTheme` would install a fresh ripple indication and reset `LocalTextStyle` even with
    // the typography held constant, and a phone that never opens this setting must stay
    // indistinguishable from upstream. `scaledTypography` returning `unscaled` itself is not
    // enough on its own for that reason.
    if (scale == 1f) {
        CompositionLocalProvider(
            LocalUnscaledTypography provides unscaled,
            LocalAppliedUiTextScale provides 1f,
            content = content,
        )
        return
    }

    val scaled = remember(unscaled, scale) { scaledTypography(unscaled, scale) }
    CompositionLocalProvider(
        LocalUnscaledTypography provides unscaled,
        LocalAppliedUiTextScale provides scale,
    ) {
        ProvideTypography(scaled, content)
    }
}

/**
 * Opts a subtree back out of [ProvideUiTextScale].
 *
 * For the settings pages' own previews - `FeedsPagePreview`, `FlowPagePreview`,
 * `TitleAndTextPreview`. Those draw the list and reading styles so the user can judge a change
 * before making it, and those styles are already sized relative to the current typography slot. If
 * the UI scale reached them, a preview would render at the interface's size rather than at the
 * size the page it previews will actually use, and the one thing a preview must not do is lie.
 *
 * A no-op when nothing has been captured, so it is safe to call from anywhere - and that no-op path
 * does not even enter [ProvideTypography], which is what keeps it free outside a scaled subtree.
 */
@Composable
fun ProvideUnscaledUiText(content: @Composable () -> Unit) {
    val unscaled = LocalUnscaledTypography.current
    if (unscaled == null) {
        content()
    } else {
        CompositionLocalProvider(LocalAppliedUiTextScale provides 1f) {
            ProvideTypography(unscaled, content)
        }
    }
}

/** Percent as stored in preferences -> the multiplier the typography is rebuilt with. */
fun uiTextScaleFactor(percent: Int): Float = percent / 100f

/**
 * Scales a literal `sp` that bypasses the typography, by the factor in force around it.
 *
 * Three rows size themselves with `.copy(fontSize = 20.sp)` rather than taking the size from a
 * typography slot - `SettingItem`, `SelectableSettingGroupItem` and `Banner`. Rebuilding the
 * typography cannot reach those, because the literal wins over the slot. They are the only three
 * places in the app that hardcode a text size, so they are handled explicitly here rather than by
 * reaching for a blunter instrument such as `LocalDensity.fontScale`.
 *
 * An identity outside every scope, so it is safe to call from anywhere.
 */
@Composable
fun uiTextScaleSp(value: TextUnit): TextUnit {
    val scale = LocalAppliedUiTextScale.current
    return if (scale == 1f) value else value * scale
}

/**
 * Re-enters [MaterialTheme] with [typography] and leaves everything else as it was.
 *
 * Every parameter is named even though `MaterialTheme` would default them to the current values.
 * Two reasons:
 *
 *  - **There are two overloads** (a 4-parameter one and a 5-parameter one that adds
 *    `motionScheme`), and `typography` alone does not pick between them. Naming `motionScheme` -
 *    which exists on only one of the two - is what makes the call unambiguous.
 *  - **`motionScheme` is not cosmetic.** `AppTheme` sets `MotionScheme.expressive()`, and a silent
 *    fall back to the standard scheme would change every animation on the settings pages while
 *    still compiling.
 *
 * Re-entering the theme does have two visible-to-the-framework side effects, both checked and
 * accepted: `LocalIndication` gets a fresh ripple instance (same configuration, so nothing renders
 * differently) and `LocalTextStyle` becomes `typography.bodyLarge` - which is exactly what the app
 * root already does, and is the behaviour we want here anyway.
 */
@Composable
private fun ProvideTypography(typography: Typography, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        motionScheme = MaterialTheme.motionScheme,
        shapes = MaterialTheme.shapes,
        typography = typography,
        content = content,
    )
}

/**
 * Rebuilds a whole [Typography] at [scale], and returns [base] itself when the scale is 1f.
 *
 * Returning the same instance rather than a copy that happens to hold the same numbers is what
 * makes "100% is an identity transform" checkable with `assertSame`, and it keeps a phone from
 * allocating a new `Typography` on every recomposition.
 *
 * Every slot has to be passed to `copy()` explicitly: `Typography.copy` fills anything omitted
 * from Material 3's *defaults*, not from the receiver, so a forgotten slot would silently snap
 * back to the stock size at 150%. `Type.kt`'s `applyFontFamily` walks the same 30 slots for the
 * same reason, and this list must keep matching it - see `FORK-NOTES.md` §2.2.
 */
fun scaledTypography(base: Typography, scale: Float): Typography =
    if (scale == 1f) {
        base
    } else {
        base.copy(
            displayLarge = base.displayLarge.scaledBy(scale),
            displayMedium = base.displayMedium.scaledBy(scale),
            displaySmall = base.displaySmall.scaledBy(scale),
            headlineLarge = base.headlineLarge.scaledBy(scale),
            headlineMedium = base.headlineMedium.scaledBy(scale),
            headlineSmall = base.headlineSmall.scaledBy(scale),
            titleLarge = base.titleLarge.scaledBy(scale),
            titleMedium = base.titleMedium.scaledBy(scale),
            titleSmall = base.titleSmall.scaledBy(scale),
            bodyLarge = base.bodyLarge.scaledBy(scale),
            bodyMedium = base.bodyMedium.scaledBy(scale),
            bodySmall = base.bodySmall.scaledBy(scale),
            labelLarge = base.labelLarge.scaledBy(scale),
            labelMedium = base.labelMedium.scaledBy(scale),
            labelSmall = base.labelSmall.scaledBy(scale),
            bodyLargeEmphasized = base.bodyLargeEmphasized.scaledBy(scale),
            bodyMediumEmphasized = base.bodyMediumEmphasized.scaledBy(scale),
            bodySmallEmphasized = base.bodySmallEmphasized.scaledBy(scale),
            displayLargeEmphasized = base.displayLargeEmphasized.scaledBy(scale),
            displayMediumEmphasized = base.displayMediumEmphasized.scaledBy(scale),
            displaySmallEmphasized = base.displaySmallEmphasized.scaledBy(scale),
            headlineLargeEmphasized = base.headlineLargeEmphasized.scaledBy(scale),
            headlineMediumEmphasized = base.headlineMediumEmphasized.scaledBy(scale),
            headlineSmallEmphasized = base.headlineSmallEmphasized.scaledBy(scale),
            titleLargeEmphasized = base.titleLargeEmphasized.scaledBy(scale),
            titleMediumEmphasized = base.titleMediumEmphasized.scaledBy(scale),
            titleSmallEmphasized = base.titleSmallEmphasized.scaledBy(scale),
            labelLargeEmphasized = base.labelLargeEmphasized.scaledBy(scale),
            labelMediumEmphasized = base.labelMediumEmphasized.scaledBy(scale),
            labelSmallEmphasized = base.labelSmallEmphasized.scaledBy(scale),
        )
    }

/**
 * `fontSize` and `lineHeight` only, matching `ListFonts.withListStyle`.
 *
 * `letterSpacing` is left alone: Material 3's tracking values are fractions of a dp, so scaling
 * them changes nothing visible, while scaling only two of the three keeps this layer's idea of
 * "what a text size is" identical to the one the list pages already use.
 */
private fun TextStyle.scaledBy(scale: Float): TextStyle =
    copy(fontSize = fontSize.scaledBy(scale), lineHeight = lineHeight.scaledBy(scale))

private fun TextUnit.scaledBy(scale: Float): TextUnit =
    if (this == TextUnit.Unspecified) this else this * scale
