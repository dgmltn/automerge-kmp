package com.dgmltn.automerge.sample.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The base of every preview here: the sample's theme, on its background. */
@Composable
fun SamplePreview(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    MaterialTheme {
        Surface(modifier = modifier) {
            Box(modifier = Modifier.padding(padding), content = content)
        }
    }
}
