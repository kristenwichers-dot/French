package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AccentKeyboardRow(
    onAccentClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val accents = listOf("é", "è", "ê", "à", "ç", "â", "î", "ô", "û", "ù", "œ", "É", "Ç")

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(accents) { char ->
            OutlinedButton(
                onClick = { onAccentClick(char) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.testTag("accent_btn_$char")
            ) {
                Text(
                    text = char,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp)
                )
            }
        }
    }
}
