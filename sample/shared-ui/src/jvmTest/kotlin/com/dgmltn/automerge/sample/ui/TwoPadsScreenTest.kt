package com.dgmltn.automerge.sample.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TwoPadsScreenTest {
    @Test
    fun typingInTheLeft_showsInTheRight() {
        runTwoPadsScreen {
            pad("Left").performTextInput("hello")

            pad("Right").assertEditableText("hello")
            onAllNodesWithText("2 changes", substring = true).assertCountEquals(2)
        }
    }

    @Test
    fun padsEditedApart_showTheMergedTextOnceLinked() {
        runTwoPadsScreen {
            pad("Left").performTextInput("one two three")
            onNodeWithText("Linked").performClick().assertIsOff()

            pad("Left").performTextReplacement("one TWO three")
            pad("Right").performTextReplacement("one two three four")
            pad("Left").assertEditableText("one TWO three")
            pad("Right").assertEditableText("one two three four")
            onNodeWithText("Linked").performClick().assertIsOn()

            pad("Left").assertEditableText("one TWO three four")
            pad("Right").assertEditableText("one TWO three four")
        }
    }

    private fun runTwoPadsScreen(block: ComposeUiTest.() -> Unit) {
        val owner =
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        try {
            runComposeUiTest {
                setContent {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                        TwoPadsScreen()
                    }
                }
                block()
            }
        } finally {
            owner.viewModelStore.clear()
        }
    }

    private fun ComposeUiTest.pad(label: String): SemanticsNodeInteraction =
        onNode(hasSetTextAction() and hasText(label))

    private fun SemanticsNodeInteraction.assertEditableText(text: String) {
        this.assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text))
        )
    }
}
