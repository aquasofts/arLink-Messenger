package com.nearlink.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nearlink.messenger.ui.navigation.NearLinkNavGraph
import com.nearlink.messenger.ui.theme.NearLinkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NearLinkTheme {
                NearLinkNavGraph()
            }
        }
    }
}
