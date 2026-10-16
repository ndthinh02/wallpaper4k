package com.example.wallpaper

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wallpaper.presentation.*
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val model by viewModels<WalperViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hold the launch window only while real persisted settings load.
        window.decorView.viewTreeObserver.addOnPreDrawListener(object :
            android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (model.settings.value == null) return false
                window.decorView.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
        enableEdgeToEdge()
        setContent {
            val settings by model.settings.collectAsStateWithLifecycle()
            settings?.let { prefs ->
                SideEffect { model.repo.language = prefs.language }
                val config = LocalConfiguration.current
                val localized = remember(config, prefs.language, prefs.theme) {
                    createConfigurationContext(Configuration(config).apply {
                        setLocale(Locale.forLanguageTag(prefs.language))
                        if (prefs.theme != "system") uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                                if (prefs.theme == "dark") Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                    })
                }
                CompositionLocalProvider(
                    LocalContext provides localized,
                    LocalConfiguration provides localized.resources.configuration,
                    LocalActivityResultRegistryOwner provides this
                ) {
                    WalperTheme(prefs.theme) { WalperApp(model, prefs, intent.getStringExtra("wallpaperId")) }
                }
            }
        }
    }
}
