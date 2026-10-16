package com.example.wallpaper.presentation

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.wallpaper.R
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

private fun NetworkCapabilities.hasInternet(): Boolean =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

private fun internetConnectivity(context: Context) = callbackFlow {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    // Initial snapshot; subsequent updates use the capabilities supplied by Android.
    trySend(manager.getNetworkCapabilities(manager.activeNetwork)?.hasInternet() == true)
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            trySend(capabilities.hasInternet())
        }

        override fun onLost(network: Network) {
            trySend(false)
        }
    }
    manager.registerDefaultNetworkCallback(callback)
    awaitClose { manager.unregisterNetworkCallback(callback) }
}

@OptIn(FlowPreview::class)
@Composable
fun NetworkLossAlert() {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val connectivity = remember(context) { internetConnectivity(context) }
    var wasOnline by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var showAlert by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(connectivity, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Allow brief Wi-Fi/mobile handovers without flashing an alert.
            connectivity.debounce { online -> if (online) 0L else 1_500L }
                .distinctUntilChanged()
                .collect { online ->
                    if (wasOnline == true && !online) showAlert = true
                    if (online) showAlert = false
                    wasOnline = online
                }
        }
    }

    if (showAlert) AlertDialog(
        onDismissRequest = { showAlert = false },
        title = { Text(stringResource(R.string.connection_lost_title)) },
        text = { Text(stringResource(R.string.connection_lost_message)) },
        confirmButton = {
            TextButton(onClick = { showAlert = false }) {
                Text(stringResource(R.string.confirm))
            }
        }
    )
}
