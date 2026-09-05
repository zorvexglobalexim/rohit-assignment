package com.rohit.videoprocessor

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.rohit.videoprocessor.navigation.AppNavGraph
import com.rohit.videoprocessor.ui.theme.Frame
import com.rohit.videoprocessor.ui.theme.FrameTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // FRAME's theme is always light (cream/green/gold), independent of the system setting -
        // status/nav bar icons are forced dark to stay legible against it, rather than following
        // whatever enableEdgeToEdge()'s default system-theme detection would otherwise pick.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        setContent {
            FrameTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Frame.colors.background) {
                    AppNavGraph()
                }
            }
        }
    }
}
