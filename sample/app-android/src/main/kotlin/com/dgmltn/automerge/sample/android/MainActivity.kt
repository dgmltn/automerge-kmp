package com.dgmltn.automerge.sample.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.dgmltn.automerge.sample.ui.TwoPadsScreen

/** Two pads on one phone; the activity's own ViewModelStore keeps them across rotation. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme { Surface { TwoPadsScreen(modifier = Modifier.safeDrawingPadding()) } }
        }
    }
}
