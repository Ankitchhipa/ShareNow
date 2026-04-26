package org.sharenow

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.sharenow.fileshare.platform.initializeAndroidFileShare

class MainActivity : ComponentActivity() {

    companion object {
        // Use a weak reference to avoid memory leaks
        var currentInstance: MainActivity? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.dark(
                Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)
        currentInstance = this
        initializeAndroidFileShare(this)

        setContent {
            App()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (currentInstance == this) {
            currentInstance = null // Clear it to prevent leaks
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}


