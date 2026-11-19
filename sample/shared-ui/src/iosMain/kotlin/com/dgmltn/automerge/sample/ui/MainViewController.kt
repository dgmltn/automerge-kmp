package com.dgmltn.automerge.sample.ui

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * The iOS app's only view controller. Swift: `MainViewControllerKt.mainViewController()`. The
 * controller supplies the ViewModelStoreOwner, so the pads live as long as it does.
 */
fun mainViewController(): UIViewController = ComposeUIViewController {
    MaterialTheme { Surface { TwoPadsScreen(modifier = Modifier.safeDrawingPadding()) } }
}
