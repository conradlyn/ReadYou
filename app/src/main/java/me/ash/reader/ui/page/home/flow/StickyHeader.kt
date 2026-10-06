package me.ash.reader.ui.page.home.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.ash.reader.ui.component.withFlowListStyle
import me.ash.reader.ui.ext.surfaceColorAtElevation
import me.ash.reader.ui.theme.palette.onDark

@Composable
fun StickyHeader(
    dateString: String,
    isShowFeedIcon: Boolean,
    articleListTonalElevation: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceColorAtElevation(articleListTonalElevation.dp)
                        onDark MaterialTheme.colorScheme.surface
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier
                .padding(
                    // Was `54.dp / 24.dp`, which is the same distance on a phone and 3.6dp short of
                    // the rows on a tablet - see `FlowListInset.kt`. The date has to sit directly
                    // over the article it dates, so it reads the same value they do.
                    start = flowListTextInset(isShowFeedIcon),
                )
                .padding(top = 8.dp, bottom = 4.dp),
            text = dateString,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge.withFlowListStyle(),
        )
    }
}