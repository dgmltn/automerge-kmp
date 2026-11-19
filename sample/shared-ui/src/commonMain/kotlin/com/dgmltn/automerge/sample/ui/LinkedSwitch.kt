package com.dgmltn.automerge.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** Whether the two pads reach each other; the whole row toggles it, not just the switch. */
@Composable
fun LinkedSwitch(
    linked: Boolean,
    onLinkedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .toggleable(value = linked, role = Role.Switch, onValueChange = onLinkedChange)
                .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Linked")
        Switch(checked = linked, onCheckedChange = null)
    }
}

@Preview
@Composable
private fun Preview_LinkedSwitch_On() {
    SamplePreview { LinkedSwitch(linked = true, onLinkedChange = {}) }
}

@Preview
@Composable
private fun Preview_LinkedSwitch_Off() {
    SamplePreview { LinkedSwitch(linked = false, onLinkedChange = {}) }
}
