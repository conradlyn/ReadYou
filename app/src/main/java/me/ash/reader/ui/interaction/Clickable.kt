package me.ash.reader.ui.interaction

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Modifier.alphaIndicationClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    // Touch screens never emit a hover interaction, so this is a no-op there. With a mouse - the
    // magnetic keyboard case and desktop mode on a tablet - it is the only feedback these
    // modifiers give: without it a pointer user gets no indication that a row is interactive,
    // because `indication = null` suppresses the ripple too.
    val isHovered by interactionSource.collectIsHoveredAsState()
    val animatedAlpha by
        animateFloatAsState(
            when {
                isPressed -> .5f
                isHovered -> .85f
                else -> 1f
            },
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        )

    return clickable(
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            indication = null,
            interactionSource = interactionSource,
            onClick = onClick,
        )
        .alpha(animatedAlpha)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Modifier.alphaIndicationSelectable(
    selected: Boolean,
    enabled: Boolean = true,
    role: Role? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    // Touch screens never emit a hover interaction, so this is a no-op there. With a mouse - the
    // magnetic keyboard case and desktop mode on a tablet - it is the only feedback these
    // modifiers give: without it a pointer user gets no indication that a row is interactive,
    // because `indication = null` suppresses the ripple too.
    val isHovered by interactionSource.collectIsHoveredAsState()
    val animatedAlpha by
        animateFloatAsState(
            when {
                isPressed -> .5f
                isHovered -> .85f
                else -> 1f
            },
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        )

    return selectable(
            selected = selected,
            enabled = enabled,
            role = role,
            indication = null,
            interactionSource = interactionSource,
            onClick = onClick,
        )
        .alpha(animatedAlpha)
}
