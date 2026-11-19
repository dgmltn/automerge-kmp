package com.dgmltn.automerge.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** One pad's text area, with how big its document is beneath it. */
@Composable
fun PadPane(
    label: String,
    field: TextFieldValue,
    byteSize: Int,
    changeCount: Int,
    onFieldChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = field,
            onValueChange = onFieldChange,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth().height(PadHeight),
        )
        Text(
            text = "$byteSize bytes · ${changesText(changeCount)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val PadHeight = 200.dp

private fun changesText(count: Int): String = if (count == 1) "1 change" else "$count changes"

@Preview
@Composable
private fun Preview_PadPane_Empty() {
    SamplePreview {
        PadPane(
            label = "Left",
            field = TextFieldValue(),
            byteSize = 98,
            changeCount = 1,
            onFieldChange = {},
        )
    }
}

@Preview
@Composable
private fun Preview_PadPane_Typed() {
    SamplePreview {
        PadPane(
            label = "Right",
            field = TextFieldValue("one TWO three four", TextRange(7)),
            byteSize = 214,
            changeCount = 4,
            onFieldChange = {},
        )
    }
}
