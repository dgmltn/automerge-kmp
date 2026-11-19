package com.dgmltn.automerge.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/** Two devices' copies of one text, side by side, and the switch that links them. */
@Composable
fun TwoPadsScreen(
    modifier: Modifier = Modifier,
    viewModel: TwoPadsViewModel = viewModel { TwoPadsViewModel() },
) {
    TwoPadsContent(
        state = viewModel.state,
        onLeftChange = viewModel::onLeftChange,
        onRightChange = viewModel::onRightChange,
        onLinkedChange = viewModel::onLinkedChange,
        modifier = modifier,
    )
}

/** Side by side when there is room for two text areas, otherwise one above the other. */
@Composable
fun TwoPadsContent(
    state: TwoPadsUiState,
    onLeftChange: (TextFieldValue) -> Unit,
    onRightChange: (TextFieldValue) -> Unit,
    onLinkedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().padding(16.dp)) {
        if (maxWidth >= SideBySideWidth) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PadPane("Left", state.left, onLeftChange, Modifier.weight(1f))
                LinkedSwitch(state.linked, onLinkedChange)
                PadPane("Right", state.right, onRightChange, Modifier.weight(1f))
            }
        } else {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PadPane("Left", state.left, onLeftChange, Modifier.fillMaxWidth())
                LinkedSwitch(state.linked, onLinkedChange)
                PadPane("Right", state.right, onRightChange, Modifier.fillMaxWidth())
            }
        }
    }
}

private val SideBySideWidth = 600.dp

@Composable
private fun PadPane(
    label: String,
    state: PadUiState,
    onFieldChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    PadPane(
        label = label,
        field = state.field,
        byteSize = state.byteSize,
        changeCount = state.changeCount,
        onFieldChange = onFieldChange,
        modifier = modifier,
    )
}

private val PreviewState =
    TwoPadsUiState(
        left =
            PadUiState(
                field = TextFieldValue("one TWO three", TextRange(index = 7)),
                byteSize = 180,
                changeCount = 3,
            ),
        right =
            PadUiState(
                field = TextFieldValue("one two three four", TextRange(index = 18)),
                byteSize = 186,
                changeCount = 3,
            ),
        linked = false,
    )

@Preview(widthDp = 900, heightDp = 360)
@Composable
private fun Preview_TwoPadsContent_Wide() {
    SamplePreview(padding = 0.dp) {
        TwoPadsContent(
            state = PreviewState,
            onLeftChange = {},
            onRightChange = {},
            onLinkedChange = {},
        )
    }
}

@Preview(widthDp = 360, heightDp = 720)
@Composable
private fun Preview_TwoPadsContent_Narrow() {
    SamplePreview(padding = 0.dp) {
        TwoPadsContent(
            state = PreviewState,
            onLeftChange = {},
            onRightChange = {},
            onLinkedChange = {},
        )
    }
}

@Preview(widthDp = 900, heightDp = 360)
@Composable
private fun Preview_TwoPadsContent_Linked() {
    SamplePreview(padding = 0.dp) {
        TwoPadsContent(
            state =
                PreviewState.copy(
                    right = PreviewState.left.copy(field = TextFieldValue("one TWO three")),
                    linked = true,
                ),
            onLeftChange = {},
            onRightChange = {},
            onLinkedChange = {},
        )
    }
}
