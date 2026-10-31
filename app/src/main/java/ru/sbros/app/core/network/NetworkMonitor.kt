package ru.sbros.app.core.network
import android.content.Context
import android.net.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

class NetworkMonitor(context: Context,scope: CoroutineScope) {
    private val manager=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private fun connected()=manager.activeNetwork?.let{manager.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}==true
    val online=callbackFlow {
        val callback=object: ConnectivityManager.NetworkCallback(){
            override fun onAvailable(network: Network){trySend(connected())}
            override fun onLost(network: Network){trySend(connected())}
            override fun onCapabilitiesChanged(network: Network,capabilities: NetworkCapabilities){trySend(connected())}
        }
        manager.registerDefaultNetworkCallback(callback);trySend(connected())
        awaitClose{manager.unregisterNetworkCallback(callback)}
    }.distinctUntilChanged().stateIn(scope,SharingStarted.Eagerly,connected())
}
