package org.akkirrai.hibiki.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class NoInternetConnectionException(message: String) : IllegalStateException(message)

fun hasActiveInternetConnection(context: Context): Boolean {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    // VALIDATED is deliberately not required: right after the device wakes or unlocks, Android re-checks
    // the network and reports it unvalidated for a moment (and activeNetwork can be briefly null), which
    // made the app claim there was no internet while it was on. A request that really cannot get
    // through fails on its own with a network error.
    val networks = listOfNotNull(connectivityManager.activeNetwork) + connectivityManager.allNetworks
    return networks.any { network ->
        connectivityManager.getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}
