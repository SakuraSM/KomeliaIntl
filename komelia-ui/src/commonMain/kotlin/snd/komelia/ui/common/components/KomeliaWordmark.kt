package snd.komelia.ui.common.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

@Composable
fun KomeliaWordmark(
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
) {
    Text(
        "KOMELIA",
        modifier = modifier,
        style = style.copy(letterSpacing = 3.sp, fontWeight = FontWeight.Medium),
        maxLines = 1,
    )
}
