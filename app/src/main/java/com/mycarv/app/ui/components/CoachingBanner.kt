package com.mycarv.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mycarv.app.analysis.FeedbackGenerator
import com.mycarv.app.ui.theme.AccentGreen
import com.mycarv.app.ui.theme.AccentOrange
import com.mycarv.app.ui.theme.AccentYellow
import com.mycarv.app.ui.theme.DarkBase

@Composable
fun CoachingBanner(
    tip: FeedbackGenerator.CoachingTip?,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible && tip != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        tip?.let {
            val bgColor = when (it.priority) {
                FeedbackGenerator.CoachingTip.Priority.HIGH -> AccentOrange
                FeedbackGenerator.CoachingTip.Priority.MEDIUM -> AccentYellow
                FeedbackGenerator.CoachingTip.Priority.LOW -> AccentGreen
            }
            val textColor = DarkBase

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = bgColor),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.TipsAndUpdates,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    Text(
                        text = it.message,
                        color = textColor,
                    )
                }
            }
        }
    }
}
