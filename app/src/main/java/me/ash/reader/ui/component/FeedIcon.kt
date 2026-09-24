package me.ash.reader.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.size.Size
import me.ash.reader.R
import me.ash.reader.ui.adaptive.adaptiveScale
import me.ash.reader.ui.adaptive.adaptiveSize
import me.ash.reader.ui.component.base.Base64Image
import me.ash.reader.ui.component.base.RYAsyncImage

/**
 * 图标在界面上最大也就 32dp 左右，即使 3x 屏也只需约 96px。
 *
 * 不给 [RYAsyncImage] 传 size 时会落到它的默认值 `Size.ORIGINAL`，即**按图片原始分辨率解码**。
 * 订阅源的 logo 常见 512×512（PWA icon）乃至 1200×630（og-image），后者解码后约 3 MB 位图
 * —— 全部为了渲染一个 20dp 的圆点。而列表每一项都有图标，订阅管理页一次列出几百个，
 * 平板上双栏同时渲染的条数更多。
 */
private val FEED_ICON_DECODE_SIZE = Size(192, 192)

@Composable
fun FeedIcon(
    modifier: Modifier = Modifier,
    feedName: String? = "",
    iconUrl: String?,
    size: Dp = 20.dp,
    placeholderIcon: ImageVector? = null,
) {
    // Scaled once, here, so every branch below draws the same circle: the base64 branch, the
    // network branch and the letter fallback would otherwise disagree on a tablet. Identity on a
    // phone, so the upstream appearance is untouched there.
    val iconSize = adaptiveSize(size)
    if (iconUrl.isNullOrEmpty()) {
        if (placeholderIcon == null) {
            FontIcon(modifier, iconSize, feedName ?: "")
        } else {
            ImageIcon(modifier, placeholderIcon, feedName ?: "")
        }
    }
    // e.g. image/gif;base64,R0lGODlh...
    else if ("^image/.*;base64,.*".toRegex().matches(iconUrl)) {
        Base64Image(
            modifier = modifier
                .size(iconSize)
                .clip(CircleShape),
            base64Uri = iconUrl,
            onEmpty = { FontIcon(modifier, iconSize, feedName ?: "") },
        )
    } else {
        RYAsyncImage(
            modifier = modifier
                .size(iconSize)
                .clip(CircleShape),
            contentDescription = feedName ?: "",
            data = iconUrl,
            placeholder = null,
            size = FEED_ICON_DECODE_SIZE,
        )
    }
}

@Composable
private fun ImageIcon(modifier: Modifier, placeholderIcon: ImageVector, feedName: String) {
    Icon(
        modifier = modifier,
        imageVector = placeholderIcon,
        contentDescription = feedName,
    )
}

@Composable
private fun FontIcon(modifier: Modifier, size: Dp, feedName: String) {
    // The letter is sized for a 20dp circle, so it has to grow with the circle or the fallback
    // looks emptier on a tablet than it does on a phone.
    val scale = adaptiveScale()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = feedName.ifEmpty { " " }.first().toString(),
            style = MaterialTheme.typography.bodyMedium.merge(
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = if (scale == 1f) 10.sp else 10.sp * scale,
                fontWeight = FontWeight.Bold,
            )
        )
    }
}

@Preview
@Composable
fun FeedIconPrev() {
    FeedIcon(feedName = stringResource(R.string.preview_feed_name), iconUrl = null)
}
