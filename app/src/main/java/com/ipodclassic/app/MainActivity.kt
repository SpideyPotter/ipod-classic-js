package com.ipodclassic.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme

class MainActivity : ComponentActivity() {
    private val model: IpodViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                IpodApp(model, onSignIn = {
                    model.authorizationUri()?.let { uri ->
                        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        catch (_: android.content.ActivityNotFoundException) { model.reportError("Install a browser to sign in to Spotify.") }
                    }
                })
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent) }
    private fun handleIntent(intent: Intent) {
        intent.data?.takeIf { it.scheme == "com.ipodclassic.app" && it.host == "callback" }?.let {
            model.callback(it)
            intent.data = null
        }
    }
    override fun onStart() { super.onStart(); model.onForeground() }
    override fun onStop() { model.onBackground(); super.onStop() }
}
